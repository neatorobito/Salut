package com.peak.salut;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;
import com.peak.salut.Callbacks.SalutCallback;
import com.peak.salut.Callbacks.SalutDeviceCallback;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;


public class Salut implements WifiP2pManager.ConnectionInfoListener{

    protected static final String TAG = "Salut";
    private static final int SALUT_SERVER_PORT = 37500;
    private static final int MAX_CLIENT_CONNECTIONS = 5;
    private static final int MAX_SERVER_CONNECTIONS = 25;
    private static final int BUFFER_SIZE = 65536;;
    private final String UNREGISTER = "UNREGISTER_SALUT_DEVICE";
    private String TTP = "._tcp";
    private SalutDataReceiver dataReceiver;
    private boolean receiverRegistered = false;

    private static WifiManager wifiManager;
    private boolean respondersAlreadySet = false;
    private boolean firstDeviceAlreadyFound = false;
    private boolean hostServerIsRunning = false;
    private SalutCallback deviceNotSupported;
    private SalutCallback onRegistered;
    private SalutCallback onRegistrationFail;
    private SalutDeviceCallback onDeviceRegisteredWithHost;

    //Service Objects
    public SalutDevice thisDevice;
    public boolean serviceIsRunning = false;
    public SalutDevice registeredHost;
    protected boolean isRunningAsHost = false;
    private ServerSocket listenerServiceSocket;
    private Socket listeningSocket;

    //WiFi P2P Objects
    private WifiP2pServiceInfo serviceInfo;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    public IntentFilter intentFilter = new IntentFilter();
    public BroadcastReceiver receiver = null;

    //Connection Objects
    public boolean isConnectedToAnotherDevice = false;
    private ServerSocket salutServerSocket;
    private Socket salutClientSocket;

    //Found Service Objects
    protected SalutDevice lastConnectedDevice;
    public ArrayList<SalutDevice> foundDevices;
    public ArrayList<SalutDevice> registeredClients;



    public Salut(SalutDataReceiver dataReceiver, SalutServiceData salutServiceData,SalutCallback deviceNotSupported)
    {
        WifiManager wifiMan = (WifiManager) dataReceiver.currentContext.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMan.getConnectionInfo();

        this.dataReceiver = dataReceiver;
        this.deviceNotSupported = deviceNotSupported;
        this.TTP = salutServiceData.serviceData.get("SERVICE_NAME") + TTP;

        thisDevice = new SalutDevice();
        thisDevice.serviceName = salutServiceData.serviceData.get("SERVICE_NAME");
        thisDevice.readableName = salutServiceData.serviceData.get("INSTANCE_NAME");
        thisDevice.instanceName = salutServiceData.serviceData.get("INSTANCE_NAME") + "-" + wifiInfo.getMacAddress().hashCode();
        thisDevice.macAddress = wifiInfo.getMacAddress();
        thisDevice.TTP = thisDevice.serviceName + TTP;
        thisDevice.servicePort = Integer.valueOf(salutServiceData.serviceData.get("SERVICE_PORT"));
        thisDevice.txtRecord = salutServiceData.serviceData;

        foundDevices = new ArrayList<>();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) dataReceiver.currentContext.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(dataReceiver.currentContext, dataReceiver.currentContext.getMainLooper(), null);

        receiver = new SalutBroadcastReciever(this, manager, channel);

        obtainSalutPortLock();

    }

    private void obtainSalutPortLock()
    {
        try {
            salutServerSocket = new ServerSocket(SALUT_SERVER_PORT, MAX_SERVER_CONNECTIONS);
            salutServerSocket.setReuseAddress(true);
            salutServerSocket.setReceiveBufferSize(BUFFER_SIZE);
            thisDevice.txtRecord.put("SALUT_SERVER_PORT", "" + SALUT_SERVER_PORT);
        }
        catch(IOException ex)
        {
            Log.e(TAG, "Failed to use standard port, another will be used instead.");

            try
            {
                salutServerSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                salutServerSocket.setReuseAddress(true);
                salutServerSocket.setReceiveBufferSize(BUFFER_SIZE);
                thisDevice.txtRecord.put("SALUT_SERVER_PORT", "" + salutServerSocket.getLocalPort());
            }
            catch (IOException ioEx)
            {
                Log.e(TAG, "Failed to get a random port, Salut will not work correctly.");
            }

        }
    }

    private void obtainServicePortLock()
    {
        try {
            listenerServiceSocket = new ServerSocket(thisDevice.servicePort, MAX_SERVER_CONNECTIONS);
            listenerServiceSocket.setReuseAddress(true);
            listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
            thisDevice.txtRecord.put("SERVICE_PORT", "" + thisDevice.servicePort);
        }
        catch(IOException ex)
        {
            Log.e(TAG, "Failed to use standard port, another will be used instead.");

            try
            {
                listenerServiceSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                listenerServiceSocket.setReuseAddress(true);
                listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
                thisDevice.txtRecord.put("SERVICE_PORT", "" + listenerServiceSocket.getLocalPort());
            }
            catch (IOException ioEx)
            {
                Log.e(TAG, "Failed to get a random port, " + thisDevice.serviceName + " will not work correctly.");
            }

        }
    }

    public ArrayList<String> getReadableFoundNames()
    {
        ArrayList<String> foundHostNames = new ArrayList<>(foundDevices.size());
        for(SalutDevice device : foundDevices)
        {
            foundHostNames.add(device.readableName);
        }

        return foundHostNames;
    }

    public ArrayList<String> getReadableRegisteredNames()
    {
        ArrayList<String> registeredNames = new ArrayList<>(registeredClients.size());
        for(SalutDevice device : registeredClients)
        {
            registeredNames.add(device.readableName);
        }

        return registeredNames;
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        /* This method is automatically called when we connect to a device.
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by the registration jobs.
         * This will automatically handle first time connections.*/

        if (isRunningAsHost && !hostServerIsRunning) {
            AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
                @Override
                public void doOnBackground() {
                    startHostRegistrationServer();
                }
            });
        }
        else if(!thisDevice.isRegistered && !info.isGroupOwner) {
            AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
                @Override
                public void doOnBackground() {
                    startRegistrationForClient(new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), SALUT_SERVER_PORT));
                }
            });
        }
    }

    public static void enableWiFi(Context context)
    {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
    }

    public static boolean isWiFiEnabled(Context context)
    {
        if(hotspotIsEnabled(context))
        {
            return true;
        }

        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    public static void disableWiFi(Context context)
    {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(false);
    }

    private void cleanUpDeviceConnection()
    {
        try {
            salutServerSocket.close();
            salutClientSocket.close();
        }
        catch (IOException IoEx)
        {
            Log.e(TAG, "Failed to close sockets.");

        }
    }

    private void startHostRegistrationServer()
    {
        try
        {
            //Create a server socket and wait for client connections. This
            //call blocks until a connection is accepted from a client.

            while(isRunningAsHost)
            {
                hostServerIsRunning = true;

                Log.d(TAG, "\nListening for registration data...");
                salutClientSocket = salutServerSocket.accept();
                salutClientSocket.setReceiveBufferSize(BUFFER_SIZE);
                salutClientSocket.setSendBufferSize(BUFFER_SIZE);

                //If this code is reached, a client has connected and transferred data.
                Log.d(TAG, "A device has connected to the server, transferring data...");

                Log.d(TAG, "Receiving client registration data...");
                DataInputStream fromClient = new DataInputStream(salutClientSocket.getInputStream());
                String serializedClient = fromClient.readUTF();
                SalutDevice clientDevice = LoganSquare.parse(serializedClient, SalutDevice.class);
                clientDevice.serviceAddress = salutClientSocket.getInetAddress().toString().replace("/", "");

                Log.d(TAG, "Sending server registration data...");
                String serializedServer = LoganSquare.serialize(thisDevice);
                DataOutputStream toClient = new DataOutputStream(salutClientSocket.getOutputStream());
                toClient.writeUTF(serializedServer);

                if(!clientDevice.isRegistered)
                {
                    Log.d(TAG, "Registered device and user: " + clientDevice);
                    clientDevice.isRegistered = true;
                    clientDevice.isSynced = true;
                    final SalutDevice finalDevice = clientDevice; //Allows us to get around having to add the final modifier earlier.
                    if(registeredClients.isEmpty())
                    {
                        startListeningForData();
                    }
                    if(onDeviceRegisteredWithHost != null)
                    {
                        dataReceiver.currentContext.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onDeviceRegisteredWithHost.call(finalDevice);
                            }
                        });
                    }
                    registeredClients.add(clientDevice);
                }
            }

        }
        catch (Exception ex) {
            Log.d(TAG, "An error occurred while executing a server thread.");
            ex.printStackTrace();
        }
        finally {
            cleanUpDeviceConnection();
            hostServerIsRunning = false;
        }
    }

    private void startRegistrationForClient(final InetSocketAddress hostDeviceAddress)
    {
        try
        {
            /**
             * Create a client socket with the host,
             * port, and timeout information.
             */
            Log.d(TAG, "\nAttempting to register this client with the server...");
            salutClientSocket = new Socket();
            salutClientSocket.connect(hostDeviceAddress);
            salutClientSocket.setReceiveBufferSize(BUFFER_SIZE);
            salutClientSocket.setSendBufferSize(BUFFER_SIZE);

            //If this code is reached, we've connected to the server and will transfer data.
            Log.d(TAG, thisDevice.deviceName + " is connected to the server, transferring registration data...");

            Log.d(TAG, "Sending client registration data to server...");
            String serializedClient = LoganSquare.serialize(thisDevice);
            DataOutputStream toClient = new DataOutputStream(salutClientSocket.getOutputStream());
            toClient.writeUTF(serializedClient);

            Log.d(TAG, "Receiving server registration data...");
            DataInputStream fromClient = new DataInputStream(salutClientSocket.getInputStream());
            String serializedServer = fromClient.readUTF();
            SalutDevice serverDevice = LoganSquare.parse(serializedServer, SalutDevice.class);
            serverDevice.serviceAddress = salutClientSocket.getInetAddress().toString().replace("/", "");
            registeredHost = serverDevice;

            Log.d(TAG, "Registered Host | " + registeredHost.deviceName);

            Log.d(TAG, "This device has been successfully registered with the host.");
            thisDevice.isRegistered = true;
            thisDevice.isSynced = true;
            dataReceiver.currentContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (onRegistered != null)
                        onRegistered.call();
                }
            });
            startListeningForData();
        }
        catch (IOException ex)
        {
            Log.d(TAG, "An error occurred while attempting to register.");
            dataReceiver.currentContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (onRegistrationFail != null)
                        onRegistrationFail.call();
                }
            });
            ex.printStackTrace();
        }
        finally {
            cleanUpDeviceConnection();
        }
    }

    private void sendData(final SalutDevice device, final Object data, @Nullable final SalutCallback onFailure)
    {
        AsyncJob.OnBackgroundJob sendData = new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {
                try
                {
                    listeningSocket = new Socket();
                    listeningSocket.setReceiveBufferSize(BUFFER_SIZE);
                    listeningSocket.setSendBufferSize(BUFFER_SIZE);

                    listeningSocket.connect(new InetSocketAddress(device.serviceAddress, device.servicePort));

                    //If this code is reached, a client has connected and transferred data.
                    Log.d(TAG, "Connected, transferring data...");
                    DataOutputStream dataStreamToOtherDevice = new DataOutputStream(listeningSocket.getOutputStream());

                    if(!data.toString().equals(UNREGISTER))
                    {
                        String dataToSend = LoganSquare.serialize(data);
                        dataStreamToOtherDevice.writeUTF(dataToSend);
                    }
                    else
                    {
                        dataStreamToOtherDevice.writeUTF(data.toString());
                        thisDevice.isRegistered = false;
                        thisDevice.isSynced = false;
                        registeredHost = null;
                    }
                    Log.d(TAG, "Successfully sent data.");

                }
                catch (IOException ex)
                {
                    Log.d(TAG, "An error occurred while sending data to a device.");
                    if(onFailure != null)
                        onFailure.call();
                    ex.printStackTrace();
                }
                finally {
                    if(data.toString().equals(UNREGISTER))
                    {
                        try
                        {
                            salutServerSocket.close();
                            salutClientSocket.close();
                        }
                        catch (IOException IoEx)
                        {
                            Log.e(TAG, "Failed to close sockets.");

                        }
                        cleanUpDeviceConnection();
                        disconnectFromDevice();
                        clientDisconnectFromDevice();
                    }
                }
            }
        };

        AsyncJob.doInBackground(sendData);
    }

    public void registerWithHost(final SalutDevice device, SalutCallback onRegistered, final SalutCallback onRegistrationFail)
    {
        this.onRegistered = onRegistered;
        this.onRegistrationFail = onRegistrationFail;
        connectToDevice(device, onRegistrationFail);
    }

    public void sendToAllDevices(final Object data, final SalutCallback onFailure)
    {
        if(isRunningAsHost)
        {
            for(SalutDevice registered : registeredClients) {
                sendData(registered, data, onFailure);
            }
        }
        else
        {
            Log.e(TAG, "You must be running as the host to invoke this method.");
        }
    }

    public void sendToHost(final Object data, final SalutCallback onFailure)
    {
        if(!isRunningAsHost && thisDevice.isRegistered)
        {
            sendData(registeredHost, data, onFailure);
        }
        else
        {
            Log.e(TAG, "You must be running as a client to invoke this method.");
        }
    }

    public void sendToDevice(final SalutDevice device, final Object data, final SalutCallback onFailure)
    {
        sendData(device, data, onFailure);
    }


    private void connectToDevice(final SalutDevice device, final SalutCallback onFailure)
    {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.macAddress;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully connected to another device.");
                lastConnectedDevice = device;
            }

            @Override
            public void onFailure(int reason) {
                onFailure.call();
                Log.e(TAG, "Failed to connect to device. ");
            }
        });
    }


    private void startListeningForData()
    {
        if(listenerServiceSocket == null)
            obtainServicePortLock();

        AsyncJob.OnBackgroundJob serviceServer = new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {
                Log.d(TAG, "Listening for service data...");
                try
                {
                    //Create a server socket and wait for client connections. This
                    //call blocks until a connection is accepted from a client.

                    while(isRunningAsHost || thisDevice.isRegistered) {

                        listeningSocket = listenerServiceSocket.accept();

                        //If this code is reached, a client has connected and transferred data.
                        //Log.d(TAG, "A device is sending data...");
                        DataInputStream dataStreamFromOtherDevice = new DataInputStream(listeningSocket.getInputStream());
                        String data = "";

                        while(dataStreamFromOtherDevice.available()>0)
                        {
                            data = dataStreamFromOtherDevice.readUTF();
                        }

                        dataStreamFromOtherDevice.close();

                        //Log.d(TAG, "\nSuccessfully received data.\n");

                        if(data.equals(UNREGISTER))
                        {
                            Log.d(TAG, "\nReceived request to unregister device\n");
                            for(SalutDevice registered : registeredClients)
                            {
                                if(registered.serviceAddress.equals(listeningSocket.getInetAddress().toString().replace("/", "")))
                                {
                                    registeredClients.remove(registered);
                                    Log.d(TAG, "\nSuccesfully unregistered device.\n");
                                }
                            }
                        }
                        else if(!data.isEmpty())
                        {
                            dataReceiver.dataCallback.onDataReceived(data);
                        }
                    }

                }
                catch (IOException ex)
                {
                    Log.d(TAG, "An error occurred while executing a server thread.");
                    ex.printStackTrace();
                }
                finally {
                    //cleanUpDeviceConnection();
                }
            }
        };

        AsyncJob.doInBackground(serviceServer);
    }

    protected void disconnectFromDevice()
    {
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if(group != null && group.isGroupOwner())
                {
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            isConnectedToAnotherDevice = false;
                            Log.d(TAG, "Disconnected from device.");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e(TAG, "Failed to disconnect from device. Reason: " + reason);
                        }
                    });
                }
            }
        });
    }

    protected void clientDisconnectFromDevice()
    {
        manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailure(int reason) {

            }
        });
    }


    public static boolean hotspotIsEnabled(Context context)
    {
        try
        {
            wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);

            return (Boolean) method.invoke(wifiManager, (Object[]) null);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            Log.d(TAG, "Failed to check tethering state, or it is not enabled.");
        }

        return false;
    }

    private void createService(final SalutCallback onFailure) {

        Log.d(TAG, "Starting " + thisDevice.serviceName + " Transport Protocol " + TTP);

        //Inject the listening port along with whatever else data that is going to be sent.
        thisDevice.txtRecord.put("LISTEN_PORT", String.valueOf(thisDevice.servicePort));

        //Create a service info object will android will actually hand out to the clients.
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(thisDevice.instanceName, TTP , thisDevice.txtRecord);

        //Register our service. The callbacks here just let us know if the service was registered correctly,
        //not necessarily whether or not we connected to a device.
        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully created " + thisDevice.serviceName + " service running on port " + thisDevice.servicePort);
                manager.createGroup(channel, null);
                serviceIsRunning = true;
                isRunningAsHost = true;
            }

            @Override
            public void onFailure(int error) {
                Log.d(TAG, "Failed to create " + thisDevice.serviceName + " : Error Code: " + error);
                if(onFailure != null)
                    onFailure.call();
            }
        });
    }

    public void unregisterClient(@Nullable SalutCallback onFailure)
    {
        if(onFailure == null)
        {
            onFailure = new SalutCallback() {
                @Override
                public void call() {

                }
            };
        }

        if(receiverRegistered)
        {
            dataReceiver.currentContext.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        if(!isConnectedToAnotherDevice)
        {
            Log.d(TAG, "Attempted to unregister, but not connected to group. The remote service may already have shutdown.");
        }
        else
        {
            sendToHost(UNREGISTER, onFailure);
        }
    }


    public void startNetworkService(@Nullable SalutDeviceCallback onDeviceRegisteredWithHost, @Nullable SalutCallback onFailure)
    {
        //In order to have a service that you create be seen, you must also actively look for other services. This is an Android bug.
        //For more information, read here. https://code.google.com/p/android/issues/detail?id=37425
        //We do not need to setup DNS responders.
        registeredClients = new ArrayList<>();

        this.onDeviceRegisteredWithHost = onDeviceRegisteredWithHost;

        if(!receiverRegistered)
        {
            dataReceiver.currentContext.registerReceiver(receiver, intentFilter);
            receiverRegistered = false;
        }

        createService(onFailure);
        discoverNetworkServices(deviceNotSupported);
    }

    private void setupDNSResponders()
    {
         /*Here, we register a listener for when services are actually found. The WiFi P2P specification notes that we need two types of
         *listeners, one for a DNS service and one for a TXT record. The DNS service listener is invoked whenever a service is found, regardless
         *of whether or not it is yours. To that determine if it is, we must compare our service name with the service name. If it is our service,
         * we simply log.*/

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName +  " " + serviceNameAndTP);

            }
        };

        /*The TXT record contains specific information about a service and it's listener can also be invoked regardless of the device. Here, we
        *double check if the device is ours, and then we go ahead and pull that specific information from it and put it into an Map. The function
        *that was passed in early is also called.*/
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {

                Log.d(TAG, "Found " + device.deviceName + " " + record.values().toString());

                for(SalutDevice found : foundDevices)
                {
                    if(found.deviceName.equals(device.deviceName))
                    {
                        return;
                    }
                }

                SalutDevice foundDevice = new SalutDevice(device, record);

                foundDevices.add(foundDevice);
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }


    private void setupDNSResponders(final SalutCallback onDeviceFound, final boolean callContinously)
    {
         /*Here, we register a listener for when services are actually found. The WiFi P2P specification notes that we need two types of
         *listeners, one for a DNS service and one for a TXT record. The DNS service listener is invoked whenever a service is found, regardless
         *of whether or not it is yours. To that determine if it is, we must compare our service name with the service name. If it is our service,
         * we simply log.*/

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName +  " " + serviceNameAndTP);

            }
        };

        /*The TXT record contains specific information about a service and it's listener can also be invoked regardless of the device. Here, we
        *double check if the device is ours, and then we go ahead and pull that specific information from it and put it into an Map. The function
        *that was passed in early is also called.*/
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {

                Log.d(TAG, "Found " + device.deviceName +  " " + record.values().toString());

                for(SalutDevice found : foundDevices)
                {
                    if(found.deviceName.equals(device.deviceName))
                    {
                        return;
                    }
                }

                SalutDevice foundDevice = new SalutDevice(device, record);

                foundDevices.add(foundDevice);
                if(!firstDeviceAlreadyFound && !callContinously)
                {
                    onDeviceFound.call();
                    firstDeviceAlreadyFound = true;
                }
                else if(firstDeviceAlreadyFound && callContinously)
                {
                    onDeviceFound.call();
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }

    private void setupDNSRespondersWithDevice(final SalutDeviceCallback onDeviceFound, final boolean callContinously)
    {
         /*Here, we register a listener for when services are actually found. The WiFi P2P specification notes that we need two types of
         *listeners, one for a DNS service and one for a TXT record. The DNS service listener is invoked whenever a service is found, regardless
         *of whether or not it is yours. To that determine if it is, we must compare our service name with the service name. If it is our service,
         * we simply log.*/

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName +  " " + serviceNameAndTP);
            }
        };

        /*The TXT record contains specific information about a service and it's listener can also be invoked regardless of the device. Here, we
        *double check if the device is ours, and then we go ahead and pull that specific information from it and put it into an Map. The function
        *that was passed in early is also called.*/
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {

                Log.d(TAG, "Found " + device.deviceName +  " " + record.values().toString());

                for(SalutDevice found : foundDevices) {
                    if (found.deviceName.equals(device.deviceName)) {
                        return;
                    }
                }

                SalutDevice foundDevice = new SalutDevice(device, record);

                foundDevices.add(foundDevice);
                if(!firstDeviceAlreadyFound && !callContinously)
                {
                    onDeviceFound.call(foundDevice);
                    firstDeviceAlreadyFound = true;
                }
                else if(firstDeviceAlreadyFound && callContinously)
                {
                    onDeviceFound.call(foundDevice);
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }

    private void devicesNotFoundInTime(final SalutCallback cleanUpFunction, final SalutCallback devicesFound, int timeout) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (foundDevices.isEmpty()) {
                    stopServiceDiscovery();
                    cleanUpFunction.call();
                } else {
                    devicesFound.call();
                }
            }
        }, timeout);
    }


    private void discoverNetworkServices(final SalutCallback deviceNotSupported)
    {
        if(!receiverRegistered)
        {
            dataReceiver.currentContext.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }

        // After attaching listeners, create a service request and initiate
        // discovery.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "Service discovery request acknowledged.");
                    }
                    @Override
                    public void onFailure(int arg0) {
                        Log.i(TAG, "Failed adding service discovery request.");
                    }
                });

        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Service discovery initiated.");
            }

            @Override
            public void onFailure(int arg0) {
                Log.d(TAG, "Service discovery has failed. Reason Code: " + arg0);
                if (arg0 == WifiP2pManager.P2P_UNSUPPORTED)
                    deviceNotSupported.call();
                if(arg0 == WifiP2pManager.NO_SERVICE_REQUESTS)
                {
                    disableWiFi(dataReceiver.currentContext);
                    enableWiFi(dataReceiver.currentContext);
                }
            }
        });

    }

    public void discoverNetworkServices(SalutDeviceCallback onDeviceFound, boolean callContinously)
    {
        if(!respondersAlreadySet)
        {
            setupDNSRespondersWithDevice(onDeviceFound, callContinously);
        }

        discoverNetworkServices(deviceNotSupported);
    }

    public void discoverNetworkServices(SalutCallback onDeviceFound, boolean callContinously)
    {
        if(!respondersAlreadySet)
        {
            setupDNSResponders(onDeviceFound, callContinously);
        }

        discoverNetworkServices(deviceNotSupported);
    }

    public void discoverNetworkServicesWithTimeout(SalutCallback onDevicesFound, SalutCallback onDevicesNotFound, int timeout)
    {
        if(!respondersAlreadySet)
        {
            setupDNSResponders();
        }

        discoverNetworkServices(deviceNotSupported);
        devicesNotFoundInTime(onDevicesNotFound, onDevicesFound, timeout);
    }

    public void stopNetworkService(final boolean disableWiFi)
    {
        isRunningAsHost = false;
        registeredHost = null;
        stopServiceDiscovery();

        if (manager != null && channel != null && serviceInfo != null) {

            manager.removeLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Could not end the service. Reason : " + reason);
                }

                @Override
                public void onSuccess() {
                    Log.d(TAG, "Successfully shutdown service.");
                    if(disableWiFi)
                    {
                        disableWiFi(dataReceiver.currentContext); //To give time for the requests to be disposed.
                    }
                    serviceIsRunning = false;
                }
            });

            respondersAlreadySet = false;
        }

    }

    public void stopServiceDiscovery()
    {
        if(isConnectedToAnotherDevice)
            disconnectFromDevice();

        if(receiverRegistered)
        {
            dataReceiver.currentContext.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        foundDevices.clear();

        if (manager != null && channel != null)
        {
            manager.removeServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Successfully removed service discovery request.");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to remove service discovery request. Reason : " + reason);
                }
            });
        }
    }
}