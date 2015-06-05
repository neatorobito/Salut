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

First, start by implementing the SalutDataCallback in the class that you would like to receive data. So for instance, an activity called MyActivity that implements SalutDataCallback would be useful. Then we need to create a HashMap of data about our service.

```
    HashMap<String, String> myAwesomeServiceData = new HashMap<>();
    
    
    //Then add data about your service to the HashMap. THE FIRST 3 ARE NECESSARY.
    
    myAwesomeServiceData.put("SERVICE_NAME", "_myAwesomeService");
    myAwesomeServiceData.put("SERVICE_PORT", "" + SERVICE_PORT);
    myAwesomeServiceData.put("INSTANCE_NAME", awesomeUsername);
    
    //Finally, create an instance.
    
            Salut wifiDirectNetwork = new Salut(new SalutDataReceiver(MyActivity, MyActivity), myAwesomeServiceData, new SalutCallback() {
                @Override
                public void call() {
                    Log.d(TAG, "Sorry, but this device does not support WiFi Direct.");
                    //Or you could show a user a dialog here saying the above.
                }
            });
```

## Contributing

Feel free to submit issue requests or forks.

## Motivation

WiFi Direct is a really cool concept, bu the APIs on Android make about as much sense as a pig flying. I've been working on an app that would not be possible without this technology however, and it's taken me forever to get it working but it's very cool. So, I'm interested in what devs can do when they have the right tools.

## TODO

Handshake on device data transfer.
Improve reliability of data transfer.
Create threads to deal with data transfer instead of backlogging.

## License

MIT License