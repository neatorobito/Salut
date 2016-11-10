package com.peak.salut;

import java.util.HashMap;

public class SalutServiceData {

    protected HashMap<String, String> serviceData;

    public SalutServiceData(String serviceName, int port, String instanceName) {
        serviceData = new HashMap<>();
        serviceData.put("SERVICE_NAME", "_" + serviceName);
        serviceData.put("SERVICE_PORT", "" + port);
        serviceData.put("INSTANCE_NAME", instanceName);
    }

    public void put(String name, String value){
      serviceData.put(name, value);
    }
}
