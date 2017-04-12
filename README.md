# Salut

Salut is a wrapper around the WiFi Direct API in Android. Before using Salut, you should at least skim over some of the documentation and recommended reading below. The library supports API 16 (Android 4.1 Jelly Bean) and up. Technically, WiFi Direct is supported on Android 4.0, but it is more reliable on 4.1 and up.

### Table of Contents

* [Dependencies](#dependencies)  
* [Installation](#installation)    
* [Usage](#usage)
  * [Getting started](#getting-started)
  * [Working with services](#working-with-services)
  * [Sending data](#sending-data)
  * [Receiving data](#receiving-data)
  * [Cleaning up](#cleaning-up)
* [More](#contributing)

### Recommended Reading

[General Overview](http://developer.android.com/guide/topics/connectivity/wifip2p.html)  
[Service Discovery](http://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct.html)  
[Power Consumption](http://www.drjukka.com/blog/wordpress/?p=95)  
[More Recommended Reading](http://www.drjukka.com/blog/wordpress/?p=81)

### WARNING

This library is currently in beta so functionality or APIs are subject to change.

### Why the name? What does it mean?

Salut is a French greeting. It's another way to say hello or goodbye. Apple's technology used in iOS to do something similar is called Bonjour.

## Dependencies

This library depends on:  

[LoganSquare (Serialization)](https://github.com/bluelinelabs/LoganSquare)  
[AsyncJob Library](https://github.com/Arasthel/AsyncJobLibrary)  

**You must include LoganSquare.** To do so, add the following to your project's build.grade.

```groovy
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.neenbedankt.gradle.plugins:android-apt:1.8'
    }
}
apply plugin: 'com.neenbedankt.android-apt'

dependencies {
    apt 'com.bluelinelabs:logansquare-compiler:1.3.4'
    compile 'com.bluelinelabs:logansquare:1.3.4'
}
```

## Installation

To install the library simply grab the newest version and it to your project's build.gradle file using [JitPack](https://jitpack.io/#markrjr/Salut).

## Usage

### [Sample Activity](https://gist.github.com/markrjr/0519268f69a5823da17b)

### Getting started

First, add the following permissions to your AndroidManifest.xml.

```xml
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <!--On Android you can't open sockets without the internet permission.-->
```

*On Andorid 6.0 and up, these permissions are given to the app automatically. [(Read more here)](http://developer.android.com/guide/topics/security/normal-permissions.html)*

Next, start by implementing the `SalutDataCallback` in the class that you would like to receive data. This callback as well as all others in the framework happen on the caller's thread.

Then, we need to create a `SalutDataReceiver` and a `SalutServiceData` object.

```java
    SalutDataReceiver dataReceiver = new SalutDataReceiver(myActivity, myActivity);
    SalutServiceData serviceData = new SalutServiceData("sas", 50489, superAwesomeUser.name);
```

`SalutDataReceiver` takes two arguments, `(Activity activity, SalutDataCallback dataCallback)`. In the example above, our activity implements `SalutDataCallback`, so we pass it in twice. Passing in an activity in general allows Salut to automatically register and unregister the neccessary broadcast receivers for your app.

`SalutServiceData` takes in a service name, a port, and an instance name. The instance name is basically a readable name that will be shown to users. So it's a good idea to make this something not cryptic. **Use relatively small strings for both the service name and readable names if you plan to support lower than Android 5.0, as there is a limitation on the size that those values can be. This is imposed by the system itself.**

Finally, create a `Salut` instance.

```java
    Salut network = new Salut(dataReceiver, serviceData, new SalutCallback() {
        @Override
        public void call() {
            Log.e(TAG, "Sorry, but this device does not support WiFi Direct.");
        }
    });
    
```

**It's a good practice when working with this library to keep a variable specific to your application indicating whether or not that instance is the host. The boolean field `isRunningAsHost` is provided as part of the framwork and does indicate in some cases if you're running as the host, but this is only based on whether or not the framework is connected to a device as the group owner and the host server is running.**

**There are obviously other scenarios in which an instance of your app may have not yet started a network service, but could still be considered the host.**

### Working with services

Once you have your instance, you can create or discover WiFi direct services.

#### HOST

```java
    network.startNetworkService(new SalutDeviceCallback() {
        @Override
        public void call(SalutDevice device) {
            Log.d(TAG, device.readableName + " has connected!");
        }
    });
```

When a device connects and is successfully registered, this callback will be fired. **You can access the entire list of registered clients using the field `registeredClients`.**

#### CLIENT

There are several methods to discover services. **Salut will only connect to found services of the same type.**

```java
    network.discoverNetworkServices(new SalutDeviceCallback() {
        @Override
        public void call(SalutDevice device) {
           Log.d(TAG, "A device has connected with the name " + device.deviceName);
        }
    }, false);
    
    //OR
    
    network.discoverNetworkServices(new SalutCallback() {
        @Override
        public void call() {
           Log.d(TAG, "All I know is that a device has connected.");
        }
    }, true);
```

For both of these methods you must pass in a boolean indicating wether or not you want your callback to be called repeatedly. So if **true**, the framework will call your callback each time a device is discovered. If **false** the framework will call your callback only once, when the first device is discovered. **Regardless of which boolean you pass in, the framework will continue to discover services until you manually call `stopServiceDiscovery()`.**

Lastly, there is the `discoverNetworkServicesWithTimeout()` method, which as its name implies, discovers devices for a set amount of time that you pass in, and then automatically calls the `stopServiceDiscovery()` method. **You can access the entire list of found devices using the `foundDevices` field of your instance.**

```java
    network.discoverNetworkServicesWithTimeout(new SalutCallback() {
        @Override
        public void call() {
            Log.d(TAG, "Look at all these devices! " + network.foundDevices.toString());
        }
    }, new SalutCallback() {
        @Override
        public void call() {
            Log.d(TAG, "Bummer, we didn't find anyone. ");
        }
    }, 5000);
```

Finally, when a device finds a prospective host, you must then call the `registerWithHost()` method.

```java
    network.registerWithHost(possibleHost, new SalutCallback() {
        @Override
        public void call() {
            Log.d(TAG, "We're now registered.");
        }
    }, new SalutCallback() {
        @Override
        public void call() {
            Log.d(TAG, "We failed to register.");
        }
    });
```
This method will actually make the devices connect using WiFi Direct. The framework then uses regular old sockets to pass data between devices. The devices will stay connected until `unregisterClient` is called client side or `stopNetworkService` is called host side.

### Crafting your data

LoganSquare is responsible for data serialization within the library. LoganSquare will not actually allow the sending of simple strings between clients. So, you'll have to create a class to wrap the data that you want to send.

```java
@JsonObject
public class Message{

    /*
     * Annotate a field that you want sent with the @JsonField marker.
     */
    @JsonField
    public String description;

    /*
     * Note that since this field isn't annotated as a
     * @JsonField, LoganSquare will ignore it when parsing
     * and serializing this class.
     */
    public int nonJsonField;
}
```

### Sending data

After clients have registered with the host, you can then invoke methods to send data to a client. On success, the data will obviously be sent and received on the other side, as of yet, onSuccess callbacks have not yet been implemented for this. So, sending data methods only provide failure callbacks.

To send data to all devices:

```java
    Message myMessage = new Message();
    myMessage.description = "See you on the other side!";
    
    network.sendToAllDevices(myMessage, new SalutCallback() {
        @Override
        public void call() {
            Log.e(TAG, "Oh no! The data failed to send.");
        }
    });
```

Only the host, which has the addresses of all devices, may invoke the above method. This may be changed in a future release to allow client devices to send data to all other client devices as well. As a current workaround, you could first send data to the host for approval and then inkvoke the above method. Below, however, are the current methods for clients.

To send data to a specific device:

```java
    Message myMessage = new Message();
    myMessage.description = "See you on the other side!";

    network.sendToDevice(deviceToSendTo, myMessage, new SalutCallback() {
        @Override
        public void call() {
            Log.e(TAG, "Oh no! The data failed to send.");
        }
    });
    
    network.sendToHost(myMessage, new SalutCallback() {
        @Override
        public void call() {
            Log.e(TAG, "Oh no! The data failed to send.");
        }
    });
    
```

### Receiving data

When your class implements the SalutDataCallback interface, it must override the `onDataReceived(Object data)` method.

**Data is sent between devices as serialized strings, and is received in this method as a `String`. To get it back to reality, you must parse it using LoganSquare.**

Data is received as a string so that you can parse it yourself instead of Salut doing it for you.

This is particularly useful because it means that you can create a sort of God object that will hold all your data types and is serializable. *(See LoganSquare's pages [here](https://github.com/bluelinelabs/LoganSquare/blob/master/docs/Models.md) and [here](https://github.com/bluelinelabs/LoganSquare/blob/master/docs/TypeConverters.md) for more information on this, it supports many built-in types.)* 

Or, you can add a header to the string indicating its type and then strip the header from the string in the `onDataReceived()` method and parse the resulting object accordlingly.

Regardless of the whatever method you choose to define serialized data, parsing the data to get it back to another object will look like following.

```java
    @Override
    public void onDataReceived(Object data) {
        Log.d(TAG, "Received network data.");
        try
        {
            Message newMessage = LoganSquare.parse((Message)data, Message.class);
            Log.d(TAG, newMessage.description);  //See you on the other side!
            //Do other stuff with data.
        }
        catch (IOException ex)
        {
            Log.e(TAG, "Failed to parse network data.");
        }
    }
```

### Cleaning up

```java
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if(MyApp.isHost)
            network.stopNetworkService(true);
        else
            network.unregisterClient(null);
    }
```

**Notice that we use our app's specific boolean.**

#### HOST

**When cleaning up host side, you must call `stopNetworkService`.** You must also pass in a 
boolean indicating whether or not you want to disable WiFi.

#### CLIENT

**When cleaning up client side, you must call `unregisterClient`.** You can optionally pass in a callback to be fired on failure to unregister.

## Contributing

Feel free to submit issues, requests, or fork the project.

## Motivation

WiFi Direct is a really cool concept, but the APIs on Android make about as much sense as a pig flying. I've been working on an app that would not be possible without this technology however, and it's taken me forever to get it working but it's very cool. So, I'm interested in what devs can do when they have the right tools.

## TODO

Handshake on device data transfer.  
~~Improve reliability of data transfer.~~ *Partially done in v0.3 thanks to AsyncJob library.*      
~~Create threads to deal with data transfer instead of backlogging.~~ *Done in v0.3*  
Remember if WiFi was enabled beforehand.  
Make data serialization modular. (Any library or method can be used.)

## License

(MIT)

```
Copyright (c) 2015 Peak Digital LLC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
