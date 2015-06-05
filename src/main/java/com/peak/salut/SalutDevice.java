package com.peak.salut;

import android.net.nsd.NsdServiceInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.util.Log;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import java.net.InetAddress;
import java.util.Map;

@JsonObject
public class SalutDevice {

    @JsonField
    public Map<String, String> txtRecord;
    @JsonField
    public String deviceName;
    @JsonField
    public String serviceName;
    @JsonField
    public String instanceName;
    @JsonField
    public String readableName;
    @JsonField
    public boolean isRegistered;
    @JsonField
    public boolean isSynced;
    @JsonField
    protected int servicePort;
    @JsonField
    protected String TTP = "._tcp.";
    @JsonField
    protected String macAddress;
    @JsonField
    protected String serviceAddress;


    public SalutDevice(){}

    public SalutDevice(WifiP2pDevice device, Map<String, String> txtRecord) {
        this.serviceName = txtRecord.get("SERVICE_NAME");
        this.readableName = txtRecord.get("INSTANCE_NAME");
        this.instanceName = txtRecord.get("INSTANCE_NAME");
        this.deviceName = device.deviceName;
        this.macAddress = device.deviceAddress;
        this.txtRecord = txtRecord;

    }


    @Override
    public String toString()
    {
        return String.format("Salut Device | Service Name: %s TTP: %s Human-Readable Name: %s", instanceName, TTP, readableName);
    }


}
