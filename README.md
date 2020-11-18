# Tempo

A Kotlin library for Android to get the current time from multiple sources: SNTP, GPS; or your own time source.

*Why is it important?*

> System.currentTimeMillis() [...] can be set by the user [...] so the time may jump backwards or forwards unpredictably.
>
> -- <cite>https://developer.android.com/reference/android/os/SystemClock.html. July, 2017.</cite>

You can check how Tempo works in this [blog post](https://medium.com/@AllanHasegawa/tempo-a-new-android-library-to-get-the-time-from-multiple-sources-276f7fcff7b7).

## Basic usage

Initialize the library in your `Application` class:

    class MyApp : Application {
        override fun onCreate() {
            Tempo.initialize(this)
            ...
        }
    }
    
After the library is initialized, you can get the time with:

    val timeNowInMs = Tempo.nowOrNull()
    
`Tempo::nowOrNull()` will return either a `Long` or a `null`.
A `null` is returned when *Tempo* has not been initialized yet.
When initialized, `Tempo::nowOrNull()` returns the current
[unix epoch time](https://www.epochconverter.com/) in milliseconds.

You can observe all the events emitted by the library:

    Tempo.addEventsListener { event -> Log.d("TempoEvent", event.toString()) }
    
## Dependency

Add the snippet below in your root build.gradle at the end of repositories:

    allprojects {
        repositories {
            ...
            maven { url 'https://jitpack.io' }
	    }
	}

Then, add the dependency to your module:
	
    dependencies {
        compile 'com.github.AllanHasegawa.Tempo:tempo:x.y.z'
    }
    
[![Release](https://jitpack.io/v/AllanHasegawa/Tempo.svg)](https://jitpack.io/#AllanHasegawa/Tempo)

## Time Sources

*Tempo* comes with two sources for time: `SlackSntpTimeSource` and `AndroidGPSTimeSource`.

### SlackSntpTimeSource

The `SlackSntpTimeSource` is the default time source. Tempo is using a SNTP client implementation from the Android Framework. However, it's named "slack" because we are not enforcing a minimum roundtrip delay. The main reason behind this decision is because users with poor connection (very common on mobile) may never get a low enough roundtrip delay to successfully complete a SNTP request; retrying will just waste battery and increate data consumption. Therefore, this is the recommended time source to be used for Android.

This time source requires an active internet connection to work.


### AndroidGPSTimeSource

The `AndroidGPSTimeSource` uses the device's GPS to get the current time. The accuracy will
vary depending on the GPS.

This time source is in a separated module because it adds the `ACCESS_FINE_LOCATION` permission.
Only use this module if you need this functionality.

To include it in your project, include the dependency:

    compile 'com.github.AllanHasegawa.Tempo:tempo-android-gps-time-source:x.y.z'
    
Then, add it during initialization:

    Tempo.initialize(this,
      timeSources = listOf(SlackSntpTimeSource(), AndroidGPSTimeSource(this))
      
*Warning*: If you are targeting Android SDK 23 or higher, you will have to request for the GPS
[permission at runtime](https://developer.android.com/training/permissions/requesting.html).

### Custom time source

You can create your own time source. Implement the `io.tempo.TimeSource`
interface and then add it during initialization:

    val customTs = MyCustomTs()
    Tempo.initialize(this,
      timeSources = listOf(customTs))
      
      
## Schedulers

A device's clock slowly drifts away from an accurate time. By default *Tempo* will periodically sync itself when the app is running.

However, because *Tempo* will not sync while the app is not running then starting the app may require a full sync, which can take time. To be able to sync even when the app is not running and making sure the app will always have a fresh cache, *Tempo* also offers a scheduler using Android's WorkManager.

To add, first include its module to your gradle build file:

    compile 'com.github.AllanHasegawa.Tempo:tempo-android-workmanager-scheduler:x.y.z'
    
Then, add it during initialization:

    Tempo.initialize(this,
      scheduler = WorkManagerScheduler(periodicIntervalMinutes = 60L))

## FAQ

1. What happens if the application gets destroyed?

By default, *Tempo* survives an application's process death.
It accomplishes it by saving its state in the app's shared preference storage.

2. What happens if the user reboots the device?

We invalidate all cache and a complete sync is required.

3. What happens if the user has no internet access, no GPS, and rebooted his phone?

Then you should use a fallback strategy, like `System.currentTimeMillis()`.

4. Will *Tempo* ever support Java?

No official support for Java because Kotlin is now the official language for Android.
However, it "should" work with Java.

## License

    Copyright (c) 2020 Allan Yoshio Hasegawa
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
     
        http://www.apache.org/licenses/LICENSE-2.0
     
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
