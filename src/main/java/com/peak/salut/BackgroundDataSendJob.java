package com.peak.salut;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;
import com.peak.salut.Callbacks.SalutCallback;

import org.apache.commons.io.Charsets;

import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class BackgroundDataSendJob implements AsyncJob.OnBackgroundJob {

    private final int BUFFER_SIZE = 65536;
    private Salut salutInstance;
    private Object data;
    private SalutCallback onFailure;
    private SalutDevice device;

    public BackgroundDataSendJob(SalutDevice device, Salut salutInstance, Object data, SalutCallback onFailure) {
        this.data = data;
        this.device = device;
        this.salutInstance = salutInstance;
        this.onFailure = onFailure;
    }

    @Override
    public void doOnBackground() {

        Log.d(Salut.TAG, "\nAttempting to send data to a device.");
        Socket dataSocket = new Socket();

        try {
            dataSocket.connect(new InetSocketAddress(device.serviceAddress, device.servicePort));
            dataSocket.setReceiveBufferSize(BUFFER_SIZE);
            dataSocket.setSendBufferSize(BUFFER_SIZE);

            //If this code is reached, a client has connected and transferred data.
            Log.d(Salut.TAG, "Connected, transferring data...");
            BufferedOutputStream dataStreamToOtherDevice = new BufferedOutputStream(dataSocket.getOutputStream());

            String dataToSend = LoganSquare.serialize(data);

            dataStreamToOtherDevice.write(dataToSend.getBytes(Charsets.UTF_8));
            dataStreamToOtherDevice.flush();
            dataStreamToOtherDevice.close();

            Log.d(Salut.TAG, "Successfully sent data.");

        } catch (Exception ex) {
            Log.d(Salut.TAG, "An error occurred while sending data to a device.");
            if (onFailure != null)
                onFailure.call();
            ex.printStackTrace();
        } finally {
            try {
                dataSocket.close();
            } catch (Exception ex) {
                Log.e(Salut.TAG, "Failed to close data socket.");
            }

        }
    }
}
