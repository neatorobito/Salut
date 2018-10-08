package com.peak.salut;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
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

//            http://stackoverflow.com/a/35446009/4411645
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = dataStreamFromOtherDevice.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            // StandardCharsets.UTF_8.name() > JDK 7
            data = result.toString("UTF-8");

            dataStreamFromOtherDevice.close();

            Log.d(Salut.TAG, "\nSuccessfully received data.\n");

            if (!data.isEmpty()) {
                new Handler(Looper.getMainLooper()).post(
                        new Runnable() {
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
