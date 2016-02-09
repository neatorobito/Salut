package com.peak.salut;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;
import com.peak.salut.Callbacks.SalutCallback;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class BackgroundClientRegistrationJob implements AsyncJob.OnBackgroundJob {

    private Salut salutInstance;
    private InetSocketAddress hostDeviceAddress;
    private final int BUFFER_SIZE = 65536;
    protected static boolean disableWiFiOnUnregister;
    protected static SalutCallback onRegistered;
    protected static SalutCallback onRegistrationFail;
    protected static SalutCallback onUnregisterSuccess;
    protected static SalutCallback onUnregisterFailure;


    public BackgroundClientRegistrationJob(Salut salutInstance, InetSocketAddress hostDeviceAddress) {
        this.hostDeviceAddress = hostDeviceAddress;
        this.salutInstance = salutInstance;
    }


    @Override
    public void doOnBackground() {
        Log.d(Salut.TAG, "\nAttempting to transfer registration data with the server...");
        Socket registrationSocket = new Socket();

        try {
            registrationSocket.connect(hostDeviceAddress);
            registrationSocket.setReceiveBufferSize(BUFFER_SIZE);
            registrationSocket.setSendBufferSize(BUFFER_SIZE);

            //If this code is reached, we've connected to the server and will transfer data.
            Log.d(Salut.TAG, salutInstance.thisDevice.deviceName + " is connected to the server, transferring registration data...");

            DataOutputStream toClient = new DataOutputStream(registrationSocket.getOutputStream());
            DataInputStream fromServer = new DataInputStream(registrationSocket.getInputStream());

            //TODO Use buffered streams.
            Log.v(Salut.TAG, "Sending client registration data to server...");
            String serializedClient = LoganSquare.serialize(salutInstance.thisDevice);
            toClient.writeUTF(serializedClient);
            toClient.flush();


            if (!salutInstance.thisDevice.isRegistered) {
                Log.v(Salut.TAG, "Receiving server registration data...");
                String serializedServer = fromServer.readUTF();
                SalutDevice serverDevice = LoganSquare.parse(serializedServer, SalutDevice.class);

                serverDevice.serviceAddress = registrationSocket.getInetAddress().toString().replace("/", "");
                salutInstance.registeredHost = serverDevice;

                Log.d(Salut.TAG, "Registered Host | " + salutInstance.registeredHost.deviceName);

                salutInstance.thisDevice.isRegistered = true;
                salutInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (onRegistered != null)
                            onRegistered.call();
                    }
                });

                salutInstance.startListeningForData();
            } else {

                String registrationCode = fromServer.readUTF(); //TODO Use to verify

                salutInstance.thisDevice.isRegistered = false;
                salutInstance.registeredHost = null;
                salutInstance.closeDataSocket();
                salutInstance.disconnectFromDevice();

                if (onUnregisterSuccess != null) //Success Callback.
                {
                    salutInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onUnregisterSuccess.call();
                        }
                    });
                }

                Log.d(Salut.TAG, "This device has successfully been unregistered from the server.");

            }

            toClient.close();
            fromServer.close();

        } catch (IOException ex) {
            ex.printStackTrace();

            Log.e(Salut.TAG, "An error occurred while attempting to register or unregister.");
            salutInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (onRegistrationFail != null && !salutInstance.thisDevice.isRegistered) //Prevents both callbacks from being called.
                        onRegistrationFail.call();
                    if (onUnregisterFailure != null)
                        onUnregisterFailure.call();

                }
            });


            if (salutInstance.thisDevice.isRegistered && salutInstance.isConnectedToAnotherDevice) {
                //Failed to unregister so an outright disconnect is necessary.
                salutInstance.disconnectFromDevice();
            }
        } finally {

            if (disableWiFiOnUnregister) {
                Salut.disableWiFi(salutInstance.dataReceiver.activity);
            }
            try {
                registrationSocket.close();
            } catch (Exception ex) {
                Log.e(Salut.TAG, "Failed to close registration socket.");
            }
        }
    }
}
