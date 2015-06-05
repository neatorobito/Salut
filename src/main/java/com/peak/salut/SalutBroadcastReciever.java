package com.peak.salut;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by markrjr on 2/4/15.
 */
public class SalutBroadcastReciever extends BroadcastReceiver {

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private Salut salutInstance;

    final static String TAG = "Salut";

    public SalutBroadcastReciever(Salut salutInstance, WifiP2pManager manager,  WifiP2pManager.Channel channel) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.salutInstance = salutInstance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

            if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.v(TAG, " WiFi P2P is no longer enabled.");
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            if (manager == null) {
                return;
            }
            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo.isConnected()) {
                //Here, we are connected to another WiFi P2P device, if necessary one can grab some extra information.
                if(!salutInstance.isRunningAsHost && !salutInstance.thisDevice.isRegistered)
                {
                    //If we've reached here it means that the Salut framework was not aware that
                    //we were previously not connected to a device. Someone may have started the application with a device connected.
                    salutInstance.disconnectFromDevice();
                    salutInstance.clientDisconnectFromDevice();
                }

                salutInstance.isConnectedToAnotherDevice = true;
                manager.requestConnectionInfo(channel, salutInstance);

            } else {

                Log.v(TAG, "Not connected to another device.");
                salutInstance.isConnectedToAnotherDevice = false;
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);

            if(salutInstance.thisDevice.deviceName == null)
            {
                salutInstance.thisDevice.deviceName = device.deviceName;
                salutInstance.thisDevice.macAddress = device.deviceAddress;
            }

            //Log.v(TAG, device.deviceName + " is now using P2P. ");
        }

    }

    @Override
    public IBinder peekService(Context myContext, Intent service) {
        return super.peekService(myContext, service);
    }


}