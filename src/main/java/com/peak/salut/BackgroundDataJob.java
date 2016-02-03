package com.peak.salut;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.net.Socket;


public class BackgroundDataJob implements AsyncJob.OnBackgroundJob {

    private Salut salutInstance;
    private Socket clientSocket;
    private String data;

    public BackgroundDataJob(Salut salutInstance, Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.salutInstance = salutInstance;
    }


    @Override
    public void doOnBackground() {
        try {
            //If this code is reached, a client has connected and transferred data.
            Log.v(Salut.TAG, "A device is sending data...");

            BufferedInputStream dataStreamFromOtherDevice = new BufferedInputStream(clientSocket.getInputStream());
            data = new String(IOUtils.toByteArray(dataStreamFromOtherDevice));
            dataStreamFromOtherDevice.close();

            Log.d(Salut.TAG, "\nSuccessfully received data.\n");

            if (!data.isEmpty()) {
                salutInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        salutInstance.dataReceiver.dataCallback.onDataReceived(data);
                    }
                });
            }
        } catch (Exception ex) {
            Log.e(Salut.TAG, "An error occurred while trying to receive data.");
            ex.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ex) {
                Log.e(Salut.TAG, "Failed to close data socket.");
            }
        }
    }
}
