# Tempo

A Kotlin library for Android to get the time from multiple sources: SNTP, GPS; or your own time source.

*Why is it important?*

> System.currentTimeMillis() [...] can be set by the user [...] so the time may jump backwards or forwards unpredictably.
>
> -- <cite>https://developer.android.com/reference/android/os/SystemClock.html. July, 2017.</cite>

## Basic usage

Initialize the library in your `Application` class:

    class MyApp : Application {
        override fun onCreate() {
            Tempo.initialize()
            ...
        }
    }
    
After the library is initialized, you can get the time with:

    val timeNowInMs = Tempo.now()
    
`Tempo::now()` will return either a `Long` or a `null`. A `null` is returned when *Tempo* has not been
initialized yet. When initialized, `Tempo::now()` returns the current
[unix epoch time](https://www.epochconverter.com/) in milliseconds.

You can observe all the events emitted by the library:

    Tempo.observeEvents().subscribe {
        if (it is Tempo.Initialized) {
            Log.i("Tempo", "Initialized!")
        }
    }
    
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

### Choosing a time source

*Tempo* will request the current time from each time source. Once the requests are completed,
the library will pick the *best* time. The *best* time is defined by the source's priority score.

When a request fails, the next successful request with highest priority score is picked.
If all requests fails, then *Tempo* will adopt a strategy (see `io.tempo.SyncRetryStrategy`) to
keep retrying.

### SlackSntpTimeSource

The `SlackSntpTimeSource` is the default time source. It implements a *loose* version of the
SNTP protocol. While it doesn't guarantee an accurate time, it should be accurate enough for most use cases.

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

You can create your own time source. For that, implement the `io.tempo.TimeSource`
interface and then add it during initialization:

    val customTs = MyCustomTs()
    Tempo.initialize(this,
      timeSources = listOf(customTs)
      
      
## Schedulers

A device's clock slowly drifts away from an accurate time. Therefore, *Tempo* also offers an
scheduler to automatically sync its time.

To add, first add its module to your gradle build file:

    compile 'com.github.AllanHasegawa.Tempo:tempo-android-job-scheduler:x.y.z'
    
Then, add it during initialization:

    Tempo.initialize(this,
      scheduler = AndroidJobScheduler(this, periodicIntervalMinutes = 60L))

This module uses the awesome [android-job](https://github.com/evernote/android-job) library.
Unfortunately it also means we are also using the `GcmNetworkManager` dependency–a really heavy
dependency. That's why you have to add it manually if you want it.

## FAQ

1. What happens if the application gets destroyed?

By default, *Tempo* survives an application's process death.
It accomplishes it by saving its state in the app's shared preference storage.

2. What happens if the user reboots the device?

We invalidate all cache and a complete sync is required.

3. What happens if the user has no internet access, no GPS, and rebooted his phone?

Then you should use a fallback strategy, like `System.currentTimeMillis()`.

4. Will *Tempo* ever support Java?

If there's enough interest. Open an issue if you would like to use it with Java.

## License

    Copyright (c) 2017 Allan Yoshio Hasegawa
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
     
        http://www.apache.org/licenses/LICENSE-2.0
     
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
