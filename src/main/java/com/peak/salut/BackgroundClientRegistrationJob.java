package com.peak.salut;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by markrjr on 6/8/15.
 */
public class BackgroundClientRegistrationJob implements AsyncJob.OnBackgroundJob{


    private Salut salutInstance;
    private InetSocketAddress hostDeviceAddress;
    private final int BUFFER_SIZE = 65536;


    public BackgroundClientRegistrationJob(Salut salutInstance, InetSocketAddress hostDeviceAddress)
    {
        this.hostDeviceAddress = hostDeviceAddress;
        this.salutInstance = salutInstance;
    }


    @Override
    public void doOnBackground() {
        Log.d(Salut.TAG, "\nAttempting to transfer registration data with the server...");
        Socket registrationSocket = new Socket();

        try
        {
            registrationSocket.connect(hostDeviceAddress);
            registrationSocket.setReceiveBufferSize(BUFFER_SIZE);
            registrationSocket.setSendBufferSize(BUFFER_SIZE);

            //If this code is reached, we've connected to the server and will transfer data.
            Log.d(Salut.TAG, salutInstance.thisDevice.deviceName + " is connected to the server, transferring registration data...");

            Log.v(Salut.TAG, "Sending client registration data to server...");
            String serializedClient = LoganSquare.serialize(salutInstance.thisDevice);
            DataOutputStream toClient = new DataOutputStream(registrationSocket.getOutputStream());
            toClient.writeUTF(serializedClient);
            toClient.flush();

            if(!salutInstance.thisDevice.isRegistered)
            {
                Log.v(Salut.TAG, "Receiving server registration data...");
                DataInputStream fromServer = new DataInputStream(registrationSocket.getInputStream());
                String serializedServer = fromServer.readUTF();
                SalutDevice serverDevice = LoganSquare.parse(serializedServer, SalutDevice.class);
                serverDevice.serviceAddress = registrationSocket.getInetAddress().toString().replace("/", "");
                salutInstance.registeredHost = serverDevice;

                Log.d(Salut.TAG, "Registered Host | " + salutInstance.registeredHost.deviceName);

                salutInstance.thisDevice.isRegistered = true;
                salutInstance.thisDevice.isSynced = true;
                salutInstance.dataReceiver.currentContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salutInstance.onRegistered != null)
                            salutInstance.onRegistered.call();
                    }
                });

                salutInstance.startListeningForData();
            }
            else {

                DataInputStream fromServer = new DataInputStream(registrationSocket.getInputStream());
                String registrationCode = fromServer.readUTF(); //TODO Use to verify.

                salutInstance.thisDevice.isRegistered = false;
                salutInstance.thisDevice.isSynced = false;
                salutInstance.registeredHost = null;
                salutInstance.cleanUpDataConnection(false);
                salutInstance.cleanUpDeviceConnection(false);
                salutInstance.clientDisconnectFromDevice();

                Log.d(Salut.TAG, "This device has successfully been unregistered from the server.");

            }
        }
        catch (IOException ex)
        {
            Log.e(Salut.TAG, "An error occurred while attempting to register.");
            salutInstance.dataReceiver.currentContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (salutInstance.onRegistrationFail != null)
                        salutInstance.onRegistrationFail.call();
                }
            });
            ex.printStackTrace();
        }
        finally {
            try
            {
                registrationSocket.close();
            }
            catch(Exception ex)
            {
                Log.e(Salut.TAG, "Failed to close registration socket.");
            }
        }
    }
}
