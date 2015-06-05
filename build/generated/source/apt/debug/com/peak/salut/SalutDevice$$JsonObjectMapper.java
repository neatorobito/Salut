package com.peak.salut;

import com.bluelinelabs.logansquare.JsonMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.util.HashMap;
import java.util.Map;

public final class SalutDevice$$JsonObjectMapper extends JsonMapper<SalutDevice> {
  @Override
  public SalutDevice parse(JsonParser jsonParser) throws IOException {
    return _parse(jsonParser);
  }

  public static SalutDevice _parse(JsonParser jsonParser) throws IOException {
    SalutDevice instance = new SalutDevice();
    if (jsonParser.getCurrentToken() == null) {
      jsonParser.nextToken();
    }
    if (jsonParser.getCurrentToken() != JsonToken.START_OBJECT) {
      jsonParser.skipChildren();
      return null;
    }
    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
      String fieldName = jsonParser.getCurrentName();
      jsonParser.nextToken();
      parseField(instance, fieldName, jsonParser);
      jsonParser.skipChildren();
    }
    return instance;
  }

  public static void parseField(SalutDevice instance, String fieldName, JsonParser jsonParser) throws IOException {
    if ("TTP".equals(fieldName)) {
      instance.TTP = jsonParser.getValueAsString(null);
    } else if ("deviceName".equals(fieldName)){
      instance.deviceName = jsonParser.getValueAsString(null);
    } else if ("instanceName".equals(fieldName)){
      instance.instanceName = jsonParser.getValueAsString(null);
    } else if ("isRegistered".equals(fieldName)){
      instance.isRegistered = jsonParser.getValueAsBoolean();
    } else if ("isSynced".equals(fieldName)){
      instance.isSynced = jsonParser.getValueAsBoolean();
    } else if ("macAddress".equals(fieldName)){
      instance.macAddress = jsonParser.getValueAsString(null);
    } else if ("readableName".equals(fieldName)){
      instance.readableName = jsonParser.getValueAsString(null);
    } else if ("serviceAddress".equals(fieldName)){
      instance.serviceAddress = jsonParser.getValueAsString(null);
    } else if ("serviceName".equals(fieldName)){
      instance.serviceName = jsonParser.getValueAsString(null);
    } else if ("servicePort".equals(fieldName)){
      instance.servicePort = jsonParser.getValueAsInt();
    } else if ("txtRecord".equals(fieldName)){
      if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
        HashMap<String, String> map1 = new HashMap<String, String>();
        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
          String key1 = jsonParser.getText();
          jsonParser.nextToken();
          if (jsonParser.getCurrentToken() == JsonToken.VALUE_NULL) {
            map1.put(key1, null);
          } else{
            map1.put(key1, jsonParser.getValueAsString(null));
          }
        }
        instance.txtRecord = map1;
      } else{
        instance.txtRecord = null;
      }
    }
  }

  @Override
  public void serialize(SalutDevice object, JsonGenerator jsonGenerator, boolean writeStartAndEnd) throws IOException {
    _serialize(object, jsonGenerator, writeStartAndEnd);
  }

  public static void _serialize(SalutDevice object, JsonGenerator jsonGenerator, boolean writeStartAndEnd) throws IOException {
    if (writeStartAndEnd) {
      jsonGenerator.writeStartObject();
    }
    jsonGenerator.writeStringField("TTP", object.TTP);
    jsonGenerator.writeStringField("deviceName", object.deviceName);
    jsonGenerator.writeStringField("instanceName", object.instanceName);
    jsonGenerator.writeBooleanField("isRegistered", object.isRegistered);
    jsonGenerator.writeBooleanField("isSynced", object.isSynced);
    jsonGenerator.writeStringField("macAddress", object.macAddress);
    jsonGenerator.writeStringField("readableName", object.readableName);
    jsonGenerator.writeStringField("serviceAddress", object.serviceAddress);
    jsonGenerator.writeStringField("serviceName", object.serviceName);
    jsonGenerator.writeNumberField("servicePort", object.servicePort);
    final Map<String, String> lslocaltxtRecord = object.txtRecord;
    if (lslocaltxtRecord != null) {
      jsonGenerator.writeFieldName("txtRecord");
      jsonGenerator.writeStartObject();
      for (Map.Entry<String, String> entry1 : lslocaltxtRecord.entrySet()) {
        jsonGenerator.writeFieldName(entry1.getKey().toString());
        if (entry1.getValue() == null) {
          jsonGenerator.writeNull();
        } else{
          jsonGenerator.writeString(entry1.getValue());
        }
      }
      jsonGenerator.writeEndObject();
    }
    if (writeStartAndEnd) {
      jsonGenerator.writeEndObject();
    }
  }
}
