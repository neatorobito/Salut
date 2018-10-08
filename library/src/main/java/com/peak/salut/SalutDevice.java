package com.peak.salut;

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Parcel;
import android.os.Parcelable;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import java.util.HashMap;
import java.util.Map;

@JsonObject
public class SalutDevice implements Parcelable {

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

    protected SalutDevice(Parcel in) {
        deviceName = in.readString();
        serviceName = in.readString();
        instanceName = in.readString();
        readableName = in.readString();
        isRegistered = in.readByte() != 0;
        servicePort = in.readInt();
        TTP = in.readString();
        macAddress = in.readString();
        serviceAddress = in.readString();
        int mapSize = in.readInt();
        if (mapSize < 0) {
            txtRecord = null;
        } else {
            txtRecord = new HashMap<>(mapSize);
            int i = 0;
            while (i < mapSize) {
                txtRecord.put(in.readString(), in.readString());
                i++;
            }
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceName);
        dest.writeString(serviceName);
        dest.writeString(instanceName);
        dest.writeString(readableName);
        dest.writeByte((byte) (isRegistered ? 1 : 0));
        dest.writeInt(servicePort);
        dest.writeString(TTP);
        dest.writeString(macAddress);
        dest.writeString(serviceAddress);
        if (txtRecord == null) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(txtRecord.size());
            for (String key : txtRecord.keySet()) {
                dest.writeString(key);
                dest.writeString(txtRecord.get(key));
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SalutDevice> CREATOR = new Creator<SalutDevice>() {
        @Override
        public SalutDevice createFromParcel(Parcel in) {
            return new SalutDevice(in);
        }

        @Override
        public SalutDevice[] newArray(int size) {
            return new SalutDevice[size];
        }
    };

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
