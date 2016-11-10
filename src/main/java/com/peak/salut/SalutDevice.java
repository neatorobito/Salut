package com.peak.salut;

import android.net.wifi.p2p.WifiP2pDevice;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

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
    protected int servicePort;
    @JsonField
    protected String TTP = "._tcp.";
    @JsonField
    protected String macAddress;
    @JsonField
    protected String serviceAddress;

    public SalutDevice() {
    }

    public SalutDevice(WifiP2pDevice device, Map<String, String> txtRecord) {
        this.serviceName = txtRecord.get("SERVICE_NAME");
        this.readableName = txtRecord.get("INSTANCE_NAME");
        this.instanceName = txtRecord.get("INSTANCE_NAME");
        this.deviceName = device.deviceName;
        this.macAddress = device.deviceAddress;
        this.txtRecord = txtRecord;

    }

    public String get(String name){
      return this.txtRecord.get(name);
    }

    public int getServicePort() {
        return servicePort;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getServiceAddress() {
        return serviceAddress;
    }

    @Override
    public String toString() {
        return String.format("Salut Device | Service Name: %s TTP: %s Human-Readable Name: %s", instanceName, TTP, readableName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SalutDevice device = (SalutDevice) o;

        if (!deviceName.equals(device.deviceName)) return false;
        return macAddress != null ? macAddress.equals(device.macAddress) : device.macAddress == null;

    }

    @Override
    public int hashCode() {
        int result = deviceName.hashCode();
        result = 31 * result + (macAddress != null ? macAddress.hashCode() : 0);
        return result;
    }
}
