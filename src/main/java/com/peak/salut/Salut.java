package com.peak.salut;

import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.peak.salut.Callbacks.SalutCallback;
import com.peak.salut.Callbacks.SalutDeviceCallback;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;


public abstract class Salut implements WifiP2pManager.ConnectionInfoListener {

    protected static final String TAG = "Salut";
    private static final int SALUT_SERVER_PORT = 37500;
    private static final int MAX_SERVER_CONNECTIONS = 25;
    private static final int BUFFER_SIZE = 65536;
    protected static final String STRING_ENCODING = "UTF-8";
    protected static final String UNREGISTER_CODE = "UNREGISTER_SALUT_DEVICE";
    protected String TTP = "._tcp";
    protected SalutDataReceiver dataReceiver;
    protected boolean receiverRegistered = false;

    private static WifiManager wifiManager;
    private boolean respondersAlreadySet = false;
    private boolean firstDeviceAlreadyFound = false;
    private boolean connectingIsCanceled = false;
    private SalutCallback deviceNotSupported;
    protected boolean registrationIsRunning = false;
    protected SalutDeviceCallback onDeviceRegisteredWithHost;
    protected SalutCallback unexpectedDisconnect;

    public SalutDevice thisDevice;
    public SalutDevice registeredHost;
    public boolean isRunningAsHost = false;
    public boolean isConnectedToAnotherDevice = false;
    public boolean isDiscovering = false;
    private ServerSocket listenerServiceSocket;
    private ServerSocket salutServerSocket;

    //WiFi P2P Objects
    private WifiP2pServiceInfo serviceInfo;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    protected IntentFilter intentFilter = new IntentFilter();
    protected BroadcastReceiver receiver = null;

    //Found Service Objects
    protected SalutDevice lastConnectedDevice;
    public ArrayList<SalutDevice> foundDevices;
    public ArrayList<SalutDevice> registeredClients;

    protected SalutDeviceCallback onDeviceUnregistered;

    public Salut(SalutDataReceiver dataReceiver, SalutServiceData salutServiceData, SalutCallback deviceNotSupported) {
        WifiManager wifiMan = (WifiManager) dataReceiver.context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMan.getConnectionInfo();

        this.dataReceiver = dataReceiver;
        this.deviceNotSupported = deviceNotSupported;
        this.TTP = salutServiceData.serviceData.get("SERVICE_NAME") + TTP;

        thisDevice = new SalutDevice();
        thisDevice.serviceName = salutServiceData.serviceData.get("SERVICE_NAME");
        thisDevice.readableName = salutServiceData.serviceData.get("INSTANCE_NAME");
        thisDevice.instanceName = "" + wifiInfo.getMacAddress().hashCode();
        thisDevice.macAddress = wifiInfo.getMacAddress();
        thisDevice.TTP = thisDevice.serviceName + TTP;
        thisDevice.servicePort = Integer.valueOf(salutServiceData.serviceData.get("SERVICE_PORT"));
        thisDevice.txtRecord = salutServiceData.serviceData;

        foundDevices = new ArrayList<>();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) dataReceiver.context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(dataReceiver.context, dataReceiver.context.getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.d(TAG, "Attempting to reinitialize channel.");
                channel = manager.initialize(Salut.this.dataReceiver.context, Salut.this.dataReceiver.context.getMainLooper(), this);
            }
        });

        receiver = new SalutBroadcastReceiver(this, manager, channel);
    }

    private void obtainSalutPortLock() {
        if (salutServerSocket == null || salutServerSocket.isClosed()) {
            try {
                salutServerSocket = new ServerSocket(SALUT_SERVER_PORT, MAX_SERVER_CONNECTIONS);
                salutServerSocket.setReuseAddress(true);
                salutServerSocket.setReceiveBufferSize(BUFFER_SIZE);
                thisDevice.txtRecord.put("SALUT_SERVER_PORT", "" + SALUT_SERVER_PORT);
            } catch (IOException ex) {
                Log.e(TAG, "Failed to use standard port, another will be used instead.");

                try {
                    salutServerSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                    salutServerSocket.setReuseAddress(true);
                    salutServerSocket.setReceiveBufferSize(BUFFER_SIZE);
                    thisDevice.txtRecord.put("SALUT_SERVER_PORT", "" + salutServerSocket.getLocalPort());
                } catch (IOException ioEx) {
                    Log.e(TAG, "Failed to get a random port, Salut will not work correctly.");
                }

            }
        }
    }

    private void obtainServicePortLock() {
        if (listenerServiceSocket == null || listenerServiceSocket.isClosed()) {
            try {
                listenerServiceSocket = new ServerSocket(thisDevice.servicePort, MAX_SERVER_CONNECTIONS);
                listenerServiceSocket.setReuseAddress(true);
                listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
                thisDevice.txtRecord.put("SERVICE_PORT", "" + thisDevice.servicePort);
            } catch (IOException ex) {
                Log.e(TAG, "Failed to use standard port, another will be used instead.");

                try {
                    listenerServiceSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                    listenerServiceSocket.setReuseAddress(true);
                    listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
                    thisDevice.txtRecord.put("SERVICE_PORT", "" + listenerServiceSocket.getLocalPort());
                } catch (IOException ioEx) {
                    Log.e(TAG, "Failed to get a random port, " + thisDevice.serviceName + " will not work correctly.");
                }

            }
        }
    }

    public void setOnDeviceUnregisteredCallback(SalutDeviceCallback callback) {
        onDeviceUnregistered = callback;
    }

    public ArrayList<String> getReadableFoundNames() {
        ArrayList<String> foundHostNames = new ArrayList<>(foundDevices.size());
        for (SalutDevice device : foundDevices) {
            foundHostNames.add(device.readableName);
        }

        return foundHostNames;
    }

    public ArrayList<String> getReadableRegisteredNames() {
        ArrayList<String> registeredNames = new ArrayList<>(registeredClients.size());
        for (SalutDevice device : registeredClients) {
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

        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {

                if (isRunningAsHost && !registrationIsRunning) {
                    if (info.groupFormed && !group.getClientList().isEmpty()) {
                        startHostRegistrationServer();
                    }
                } else if (!thisDevice.isRegistered && !info.isGroupOwner) {
                    if (serviceRequest == null) {
                        //This means that discoverNetworkServices was never called and we're still connected to an old host for some reason.
                        Log.e(Salut.TAG, "This device is still connected to an old host for some reason. A forced disconnect will be attempted.");
                        forceDisconnect();
                    }
                    Log.v(Salut.TAG, "Successfully connected to another device.");
                    startRegistrationForClient(new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), SALUT_SERVER_PORT));
                }
            }
        });
    }

    public static void enableWiFi(Context context) {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
    }

    public static boolean isWiFiEnabled(Context context) {
        if (hotspotIsEnabled(context)) {
            return false;
        }

        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    public static void disableWiFi(Context context) {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(false);
    }

    public static boolean hotspotIsEnabled(Context context) {
        try {
            wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);

            return (Boolean) method.invoke(wifiManager, (Object[]) null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.d(TAG, "Failed to check tethering state, or it is not enabled.");
        }

        return false;
    }

    protected void closeRegistrationSocket() {
        try {
            if (registrationIsRunning) {
                salutServerSocket.close();
                Log.v(TAG, "Registration sockets now closed.");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to close registration socket.");
        }

        registrationIsRunning = false;
    }

    protected void closeDataSocket() {
        try {
            listenerServiceSocket.close();
            Log.v(TAG, "Stopped listening for service data.");
        } catch (Exception ex) {
            Log.e(TAG, "Failed to close listening socket.");
        }
    }

    private void startRegistrationForClient(final InetSocketAddress hostDeviceAddress) {

        BackgroundClientRegistrationJob registrationJob = new BackgroundClientRegistrationJob(this, hostDeviceAddress);
        AsyncJob.doInBackground(registrationJob);

    }

    private void sendData(final SalutDevice device, final Object data, @Nullable final SalutCallback onFailure) {
        BackgroundDataSendJob sendDataToDevice = new BackgroundDataSendJob(device, this, data, onFailure);
        AsyncJob.doInBackground(sendDataToDevice);
    }

    private void startHostRegistrationServer() {
        obtainSalutPortLock();

        AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {

                try {
                    //Create a server socket and wait for client connections. This
                    //call blocks until a connection is accepted from a client.
                    registrationIsRunning = true;
                    while (isRunningAsHost) {
                        Log.d(TAG, "\nListening for registration data...");
                        Socket clientSocket = salutServerSocket.accept();
                        BackgroundServerRegistrationJob registrationJob = new BackgroundServerRegistrationJob(Salut.this, clientSocket);

                        AsyncJob.doInBackground(registrationJob);
                    }
                    registrationIsRunning = false;
                } catch (Exception ex) {
                    Log.e(TAG, "An error has occurred within the registration server thread.");
                    ex.printStackTrace();
                }
            }
        });
    }

    protected void startListeningForData() {
        obtainServicePortLock();

        AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {
                try {
                    //Create a server socket and wait for client connections. This
                    //call blocks until a connection is accepted from a client.

                    while (isRunningAsHost || thisDevice.isRegistered) {

                        Log.d(TAG, "\nListening for service data...");

                        Socket dataListener = listenerServiceSocket.accept();
                        BackgroundDataJob dealWithData = new BackgroundDataJob(Salut.this, dataListener);

                        AsyncJob.doInBackground(dealWithData);
                    }

                } catch (Exception ex) {
                    Log.e(TAG, "An error has occurred within the data listening server thread.");
                    ex.printStackTrace();
                }
            }
        });
    }

    public void registerWithHost(final SalutDevice device, @Nullable SalutCallback onRegistered, @Nullable final SalutCallback onRegistrationFail) {
        BackgroundClientRegistrationJob.onRegistered = onRegistered;
        BackgroundClientRegistrationJob.onRegistrationFail = onRegistrationFail;
        this.unexpectedDisconnect = onRegistrationFail;
        connectToDevice(device, onRegistrationFail);
    }

    public void sendToAllDevices(final Object data, @Nullable final SalutCallback onFailure) {
        if (isRunningAsHost) {
            for (SalutDevice registered : registeredClients) {
                sendData(registered, data, onFailure);
            }
        } else {
            Log.e(TAG, "This device is not the host and therefore cannot invoke this method.");
        }
    }

    public void sendToHost(final Object data, @Nullable final SalutCallback onFailure) {
        if (!isRunningAsHost && thisDevice.isRegistered) {
            sendData(registeredHost, data, onFailure);
        } else {
            Log.e(TAG, "This device is not either not registered or is the host.");
        }
    }

    public void sendToDevice(final SalutDevice device, final Object data, @Nullable final SalutCallback onFailure) {
        sendData(device, data, onFailure);
    }

    public void cancelConnecting() {
        manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "Attempting to cancel connect.");
            }

            @Override
            public void onFailure(int reason) {
                Log.v(TAG, "Failed to cancel connect, the device may not have been trying to connect.");
            }
        });

        stopServiceDiscovery(true);
        connectingIsCanceled = true;
    }

    private void connectToDevice(final SalutDevice device, final SalutCallback onFailure) {

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.macAddress;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Attempting to connect to another device.");
                lastConnectedDevice = device;
            }

            @Override
            public void onFailure(int reason) {
                onFailure.call();
                Log.e(TAG, "Failed to connect to device. ");
            }
        });
    }

    private void deleteGroup(WifiP2pManager manager, WifiP2pManager.Channel channel, WifiP2pGroup wifiP2pGroup) {
        try {
            Method getNetworkId = WifiP2pGroup.class.getMethod("getNetworkId");
            Integer networkId = (Integer) getNetworkId.invoke(wifiP2pGroup);
            Method deletePersistentGroup = WifiP2pManager.class.getMethod("deletePersistentGroup",
                    WifiP2pManager.Channel.class, Integer.class, WifiP2pManager.ActionListener.class);
            deletePersistentGroup.invoke(manager, channel, networkId, null);
        } catch (Exception ex) {
            Log.v(Salut.TAG, "Failed to delete persistent group.");
        }
    }

    protected void forceDisconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            WifiP2pManager.ActionListener doNothing = new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {

                }

                @Override
                public void onFailure(int reason) {

                }
            };

            stopServiceDiscovery(false);
            manager.cancelConnect(channel, doNothing);
            manager.clearLocalServices(channel, doNothing);
            manager.clearServiceRequests(channel, doNothing);
            manager.stopPeerDiscovery(channel, doNothing);
        }
    }

    protected void disconnectFromDevice() {
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(final WifiP2pGroup group) {
                if (group != null) {
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            isConnectedToAnotherDevice = false;
                            deleteGroup(manager, channel, group);
                            Log.d(TAG, "Removed WiFi Direct Group.");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e(TAG, "Failed to remove a WiFi Direct Group. Reason: " + reason);
                        }
                    });
                }
            }
        });
    }

    public void createGroup(final SalutCallback onSuccess, final SalutCallback onFailure) {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "Successfully created group.");
                Log.d(TAG, "Successfully created " + thisDevice.serviceName + " service running on port " + thisDevice.servicePort);
                isRunningAsHost = true;
                if (onSuccess != null) {
                    onSuccess.call();
                }
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to create group. Reason :" + reason);
                if (onFailure != null)
                    onFailure.call();
            }
        });

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void createService(final SalutCallback onSuccess, final SalutCallback onFailure) {

        manager.clearLocalServices(channel, null);

        Log.d(TAG, "Starting " + thisDevice.serviceName + " Transport Protocol " + TTP);

        //Inject the listening port along with whatever else data that is going to be sent.
        thisDevice.txtRecord.put("LISTEN_PORT", String.valueOf(thisDevice.servicePort));

        //Create a service info object will android will actually hand out to the clients.
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(thisDevice.instanceName, TTP, thisDevice.txtRecord);

        //Register our service. The callbacks here just let us know if the service was registered correctly,
        //not necessarily whether or not we connected to a device.
        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "Successfully added the local service.");
                if (onSuccess != null)
                    onSuccess.call();

            }

            @Override
            public void onFailure(int error) {
                Log.e(TAG, "Failed to create " + thisDevice.serviceName + " : Error Code: " + error);
                if (onFailure != null)
                    onFailure.call();
            }
        });
    }

    public void unregisterClient(@Nullable SalutCallback onSuccess, @Nullable SalutCallback onFailure, boolean disableWiFi) {

        BackgroundClientRegistrationJob.onUnregisterSuccess = onSuccess;
        BackgroundClientRegistrationJob.onUnregisterFailure = onFailure;
        BackgroundClientRegistrationJob.disableWiFiOnUnregister = disableWiFi;

        if (receiverRegistered) {
            dataReceiver.context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        if (!isConnectedToAnotherDevice) {
            Log.d(TAG, "Attempted to unregister, but not connected to group. The remote service may already have shutdown.");
            thisDevice.isRegistered = false;
            registeredHost = null;
            closeDataSocket();
            disconnectFromDevice();
            if (onSuccess != null) {
                onSuccess.call();
            }
        } else {
            startRegistrationForClient(new InetSocketAddress(registeredHost.serviceAddress, SALUT_SERVER_PORT));
        }
    }

    public void unregisterClient(boolean disableWiFi) {
        unregisterClient(null, null, disableWiFi);
    }

    public void startNetworkService(SalutDeviceCallback onDeviceRegisteredWithHost) {
        startNetworkService(onDeviceRegisteredWithHost, null, null);
    }

    public void startNetworkService(@Nullable SalutDeviceCallback onDeviceRegisteredWithHost, @Nullable SalutCallback onSuccess, @Nullable SalutCallback onFailure) {
        //In order to have a service that you create be seen, you must also actively look for other services. This is an Android bug.
        //For more information, read here. https://code.google.com/p/android/issues/detail?id=37425
        //We do not need to setup DNS responders.
        registeredClients = new ArrayList<>();

        this.onDeviceRegisteredWithHost = onDeviceRegisteredWithHost;

        if (!receiverRegistered) {
            dataReceiver.context.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }

        createService(onSuccess, onFailure);
        discoverNetworkServices(deviceNotSupported);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupDNSResponders() {
         /*Here, we register a listener for when services are actually found. The WiFi P2P specification notes that we need two types of
         *listeners, one for a DNS service and one for a TXT record. The DNS service listener is invoked whenever a service is found, regardless
         *of whether or not it is yours. To that determine if it is, we must compare our service name with the service name which is done in the
         * txtRecordListner below this ServiceResponseListener. Here, we simply log whether or not we've found a service.*/

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName + " " + serviceNameAndTP);

            }
        };

        /*The TXT record contains specific information about a service and it's listener can also be invoked regardless of the device. Here, we
        *double check if the device is running our service, and then we go ahead and pull that specific information from it and put it into an Map.*/
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {

                if (!foundDevices.isEmpty()) {
                    for (SalutDevice found : foundDevices) {
                        if (found.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }

                if (record.containsValue(thisDevice.serviceName)) {
                    SalutDevice foundDevice = new SalutDevice(device, record);
                    foundDevices.add(foundDevice);
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupDNSResponders(final SalutCallback onDeviceFound, final boolean callContinously) {
         /*Here, we register a listener for when services are actually found. The WiFi P2P specification notes that we need two types of
         *listeners, one for a DNS service and one for a TXT record. The DNS service listener is invoked whenever a service is found, regardless
         *of whether or not it is yours. To that determine if it is, we must compare our service name with the service name. If it is our service,
         * we simply log.*/

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName + " " + serviceNameAndTP);

            }
        };

        /*The TXT record contains specific information about a service and it's listener can also be invoked regardless of the device. Here, we
        *double check if the device is ours, and then we go ahead and pull that specific information from it and put it into an Map. The function
        *that was passed in early is also called.*/
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {

                if (!foundDevices.isEmpty()) {
                    for (SalutDevice found : foundDevices) {
                        if (found.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }

                if (record.containsValue(thisDevice.serviceName)) {
                    SalutDevice foundDevice = new SalutDevice(device, record);
                    foundDevices.add(foundDevice);

                    if (callContinously) {
                        onDeviceFound.call();
                    } else {
                        if (!firstDeviceAlreadyFound) {
                            onDeviceFound.call();
                            firstDeviceAlreadyFound = true;
                        }
                    }
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupDNSRespondersWithDevice(final SalutDeviceCallback onDeviceFound, final boolean callContinously) {
         /*Here, we register a listener for when services are actually found. The WiFi P2P specification notes that we need two types of
         *listeners, one for a DNS service and one for a TXT record. The DNS service listener is invoked whenever a service is found, regardless
         *of whether or not it is yours. To that determine if it is, we must compare our service name with the service name. If it is our service,
         * we simply log.*/

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName + " " + serviceNameAndTP);
            }
        };

        /*The TXT record contains specific information about a service and it's listener can also be invoked regardless of the device. Here, we
        *double check if the device is ours, and then we go ahead and pull that specific information from it and put it into an Map. The function
        *that was passed in early is also called.*/
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {


                if (!foundDevices.isEmpty()) {
                    for (SalutDevice found : foundDevices) {
                        if (found.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }

                if (record.containsValue(thisDevice.serviceName)) {
                    SalutDevice foundDevice = new SalutDevice(device, record);

                    foundDevices.add(foundDevice);
                    if (callContinously) {
                        onDeviceFound.call(foundDevice);
                    } else {
                        if (!firstDeviceAlreadyFound) {
                            onDeviceFound.call(foundDevice);
                            firstDeviceAlreadyFound = true;
                        }
                    }
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
                if (connectingIsCanceled) {
                    connectingIsCanceled = false;
                    cleanUpFunction.call();
                } else {
                    if (foundDevices.isEmpty()) {
                        cleanUpFunction.call();
                    } else {
                        devicesFound.call();
                    }
                    stopServiceDiscovery(false);
                }
            }
        }, timeout);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void discoverNetworkServices(final SalutCallback deviceNotSupported) {
        isDiscovering = true;

        foundDevices.clear();

        if (!receiverRegistered) {
            Log.v(Salut.TAG, "Registered Salut reciever.");
            dataReceiver.context.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }

        // After attaching listeners, create a service request and initiate
        // discovery.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.v(TAG, "Service discovery request acknowledged.");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Log.e(TAG, "Failed adding service discovery request.");
                    }
                });

        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Service discovery initiated.");
            }

            @Override
            public void onFailure(int arg0) {
                Log.e(TAG, "Service discovery has failed. Reason Code: " + arg0);
                if (arg0 == WifiP2pManager.P2P_UNSUPPORTED)
                    deviceNotSupported.call();
                if (arg0 == WifiP2pManager.NO_SERVICE_REQUESTS) {
                    disableWiFi(dataReceiver.context);
                    enableWiFi(dataReceiver.context);
                }
            }
        });

    }

    public void discoverNetworkServices(SalutDeviceCallback onDeviceFound, boolean callContinously) {
        if (!respondersAlreadySet) {
            setupDNSRespondersWithDevice(onDeviceFound, callContinously);
        }

        discoverNetworkServices(deviceNotSupported);
    }

    public void discoverNetworkServices(SalutCallback onDeviceFound, boolean callContinously) {
        if (!respondersAlreadySet) {
            setupDNSResponders(onDeviceFound, callContinously);
        }

        discoverNetworkServices(deviceNotSupported);
    }

    public void discoverWithTimeout(SalutCallback onDevicesFound, SalutCallback onDevicesNotFound, int timeout) {
        if (!respondersAlreadySet) {
            setupDNSResponders();
        }

        discoverNetworkServices(deviceNotSupported);
        devicesNotFoundInTime(onDevicesNotFound, onDevicesFound, timeout);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopNetworkService(final boolean disableWiFi) {
        if (isRunningAsHost) {
            Log.v(TAG, "Stopping network service...");
            stopServiceDiscovery(true);
            closeDataSocket();
            closeRegistrationSocket();

            if (manager != null && channel != null && serviceInfo != null) {

                manager.removeLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "Could not end the service. Reason : " + reason);
                    }

                    @Override
                    public void onSuccess() {
                        Log.v(TAG, "Successfully shutdown service.");
                        if (disableWiFi) {
                            disableWiFi(dataReceiver.context); //Called here to give time for request to be disposed.
                        }
                        isRunningAsHost = false;
                    }
                });

                respondersAlreadySet = false;
            }

        } else {
            Log.d(TAG, "Network service is not running.");
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopServiceDiscovery(boolean shouldUnregister) {
        isDiscovering = false;
        firstDeviceAlreadyFound = false;

        if (isConnectedToAnotherDevice)
            disconnectFromDevice();

        if (shouldUnregister) {
            Log.v(Salut.TAG, "Unregistered Salut reciever.");
            dataReceiver.context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        if (manager != null && channel != null) {
            manager.removeServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.v(TAG, "Successfully removed service discovery request.");
                }

                @Override
                public void onFailure(int reason) {
                    Log.v(TAG, "Failed to remove service discovery request. Reason : " + reason);
                }
            });
        }
    }

    public abstract String serialize(Object o);
}
