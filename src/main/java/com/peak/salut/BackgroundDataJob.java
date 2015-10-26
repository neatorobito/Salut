package com.peak.salut;

import android.util.Log;
import com.arasthel.asyncjob.AsyncJob;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.Socket;


public class BackgroundDataJob implements AsyncJob.OnBackgroundJob{

    private Salut salutInstance;
    private Socket clientSocket;
    private String data;

    public BackgroundDataJob(Salut salutInstance, Socket clientSocket)
    {
        this.clientSocket = clientSocket;
        this.salutInstance = salutInstance;
    }


    @Override
    public void doOnBackground() {
        try
        {
            //If this code is reached, a client has connected and transferred data.
            Log.v(Salut.TAG, "A device is sending data...");
            BufferedInputStream bufferedRead = new BufferedInputStream(clientSocket.getInputStream());
            DataInputStream dataStreamFromOtherDevice = new DataInputStream(bufferedRead);
            data = dataStreamFromOtherDevice.readUTF();
            dataStreamFromOtherDevice.close();

            Log.d(Salut.TAG, "\nSuccessfully received data.\n");

            if(!data.isEmpty())
            {
                salutInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        salutInstance.dataReceiver.dataCallback.onDataReceived(data);
                    }
                });
            }
        }
        catch(Exception ex)
        {
            Log.e(Salut.TAG, "An error occurred while trying to receive data.");
            ex.printStackTrace();
        }
        finally {
            try
            {
                clientSocket.close();
            }
            catch (Exception ex)
            {
                Log.e(Salut.TAG, "Failed to close data socket.");
            }
        }
    }
}
