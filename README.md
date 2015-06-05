# Salut

Salut is a wrapper around the WiFi Direct Service discovery API in Android. Before using Salut, you should at least skim over the documentation below. The library supports API 16 (Android 4.1 Jelly Bean) and up. Technically, WiFi direct is supported on Android 4.0, but it is more reliable on 4.0 and up.

[General Overview](http://developer.android.com/guide/topics/connectivity/wifip2p.html)
[Service Discovery](http://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct.html)
[More Recommended Reading](http://www.drjukka.com/blog/wordpress/?p=81)
[Power Consumption](http://www.drjukka.com/blog/wordpress/?p=95)

###WARNING
This library is currently in beta so functionality or APIs are subject to change.

## Dependencies

This library depends on:
[LoganSquare (Serialization)](https://github.com/bluelinelabs/LoganSquare)
###You must also include LoganSquare in your project in order to unserialize data.
[AsyncJob Library](https://github.com/Arasthel/AsyncJobLibrary)

## Installation

To install the library simply download the AAR from [here](http://google.com), and add it as well as LoganSquare to your project's build.gradle file.

```
    //This goes below the android section in build.gradle.
    buildscript {
        repositories {
            jcenter()
        }
        dependencies {
            classpath 'com.neenbedankt.gradle.plugins:android-apt:1.4'
        }
    }

    apply plugin: 'com.neenbedankt.android-apt'

    dependencies {
        apt 'com.bluelinelabs:logansquare-compiler:1.1.0'
        compile 'com.bluelinelabs:logansquare:1.1.0'
        //Rest of dependencies.
    }
```

## Usage

###Getting started

First, start by implementing the SalutDataCallback in the class that you would like to receive data. So for instance, an activity called MyActivity that implements SalutDataCallback would be useful. Then, we need to create a SalutDataReceiver and a SalutServiceData object.

Passing in your activity as the first argument takes care of registering a broadcast receiver for your app.

```
    SalutDataReceiver dataReceiver = new SalutDataReceiver(myActivity, myActivity);

    /*The SalutServiceData takes in a service name, which should be lowercase,
      a port, and an instance name. The instance name is basically a readable name that               will be shown to users. So it's good to make this something not cryptic. */

    SalutServiceData serviceData = new SalutServiceData("superAwesomeService", 50489, superAwesomeUser.name);

    //Finally, create an instance.
    Salut network = new Salut(dataReceiver, serviceData, new SalutCallback() {
        @Override
        public void call() {
            wiFiFailureDiag.show();
            //OR
            Log.d(TAG, "Sorry, but this device does not support WiFi Direct.");
        }
    });
    
```
###Working with services.
Once you have your instance, you can create or discover WiFi direct services.

###HOST
```
    network.startNetworkService(new SalutDeviceCallback() {
        @Override
        public void call(SalutDevice device) {
            Log.d(TAG, device.readableName + " has connected!");
        }
    });
```

###CLIENT
There are several methods to discover services.

```
    network.discoverNetworkServices(new SalutDeviceCallback() {
        @Override
        public void call(SalutDevice device) {

        }
    }, false);
    
    //OR
    
    network.discoverNetworkServices(new SalutDeviceCallback() {
        @Override
        public void call(SalutDevice device) {

        }
    }, true);
```

For both of these methods you must pass in a boolean indicating wether or not you want your callback to be called repeatedly. So if **true**, then the framework will call your callback each time a device is discovered. If **false** the framework will call your callback only once, when the first device is discovered. **With each of these methods, you must also manually call `stopServiceDiscovery()`.**

Lastly, there is the `discoverNetworkServicesWithTimeout()` method, which as it's name implies, discovers devices for a set amount of time that you pass in, and then automatically calls the `stopServiceDiscovery()` method. **You can access the list of found devices using the **`foundDevices`** field of your instance.

```
    Mixen.network.discoverNetworkServicesWithTimeout(new SalutCallback() {
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


###Sending data

When a device connects finds and connects to the host, it will register with said host and
then fire this callback when registered giving you access to the device as well.

After clients have registered with the host, you can then invoke methods to send data to a client. On success, the data will obviously be sent and received on the other side, so there is no reason for a callback. So, sending data methods only provide failure callbacks.

```
    network.sendToAllDevices(myData, new SalutCallback() {
        @Override
        public void call() {
            Log.e(TAG, "Oh no! The data failed to send.");
        }
    });
```

Only the host, which has the addresses of all devices may invoke the above method. There are methods for clients however.

```
    network.sendToDevice(deviceToSendTo, myData, new SalutCallback() {
        @Override
        public void call() {
            Log.e(TAG, "Oh no! The data failed to send.");
        }
    });
    
    network.sendToHost(myData, new SalutCallback() {
        @Override
        public void call() {
            Log.e(TAG, "Oh no! The data failed to send.");
        }
    });
    
```

###Receiving data
When your class implements the SalutDataCallback interface, it must override the `onDataReceived(Object)` method. **If you want to update the UI in this method, be sure to use    `runOnUiThread`.**

**Data is sent between devices as serialized strings, and is recieved in this method as a `String`. To get it back to reality, you must parse it using LoganSquare.**

Data is received as a string so that you can parse it yourself instead of Salut doing it for you.

This is particularly useful because it means that you can create a sort of God object that will hold all your data types and is serializable. *(See LoganSquare's pages [here](https://github.com/bluelinelabs/LoganSquare/blob/master/docs/Models.md) and [here](https://github.com/bluelinelabs/LoganSquare/blob/master/docs/TypeConverters.md)for more information on this, it supports many built-in types.)* 

Or, you can add a header to the string indicating it's type and then strip the header from the string in the `onDataReceived()` method and parse the resulting object accordlingly.

```
    @Override
    public void onDataReceived(Object data) {
        Log.d(TAG, "Received network data.");
        try
        {
            MyData mydata = LoganSquare.parse((String)data, MyData.class);
            Log.d(TAG, myData.superGreatField.toString());
            //Do other stuff with data.
        }
        catch (IOException ex)
        {
            Log.e(Mixen.TAG, "Failed to parse network data.");
        }
    }
```

###Cleaning up

```
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if(network.isRunningAsHost)
            network.stopNetworkService(true);
        else
            network.unregisterClient(null);
    }
```

###HOST
**When cleaning up host side, you must call `stopNetworkService`.** You must also pass in a 
boolean indicating whether or not you want to disable WiFi.

###CLIENT
**When cleaning up client side, you must call `unregisterClient`.** You can optionally pass in a callback to be fired on failure to unregister.

## Contributing

Feel free to submit issue requests or forks.

## Motivation

WiFi Direct is a really cool concept, bu the APIs on Android make about as much sense as a pig flying. I've been working on an app that would not be possible without this technology however, and it's taken me forever to get it working but it's very cool. So, I'm interested in what devs can do when they have the right tools.

## TODO

Handshake on device data transfer.
Improve reliability of data transfer.
Create threads to deal with data transfer instead of backlogging.
Remember if WiFi was enabled beforehand.

## License

MIT License