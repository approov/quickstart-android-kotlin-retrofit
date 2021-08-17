# Approov Quickstart: Android Kotlin Retrofit

This quickstart is written specifically for native Android apps that are written in Kotlin and use [`Retrofit`](https://square.github.io/retrofit/) for making the API calls that you wish to protect with Approov. If this is not your situation then check if there is a more relevant Quickstart guide available.

This quickstart provides the basic steps for integrating Approov into your app. A more detailed step-by-step guide using a [Shapes App Example](https://github.com/approov/quickstart-android-kotlin-retrofit/blob/master/SHAPES-EXAMPLE.md) is also available.

To follow this guide you should have received an onboarding email for a trial or paid Approov account.

## ADDING APPROOVSERVICE DEPENDENCY
The Approov integration is available via [`jitpack`](https://jitpack.io). This allows inclusion into the project by simply specifying a dependency in the `gradle` files for the app.

Firstly, `jitpack` needs to be added to the end the `repositories` section in the `build.gradle` file at the top root level of the project:

```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Secondly, add the dependency in your app's `build.gradle`:


```
dependencies {
	implementation 'com.github.approov:approov-service-retrofit:2.7.0'
}
```

Make sure you do a Gradle sync (by selecting `Sync Now` in the banner at the top of the modified `.gradle` file) after making these changes.

This package is actually an open source wrapper layer that allows you to easily use Approov with `Retrofit`. This has a further dependency to the closed source [Approov SDK](https://github.com/approov/approov-android-sdk).

Make sure you do a Gradle sync (by selecting `Sync Now` in the banner at the top of the modified `.gradle` file) after making these changes.

## MANIFEST CHANGES
The following app permissions need to be available in the manifest to use Approov:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
```

Note that the minimum SDK version you can use with the Approov package is 21 (Android 5.0). 

Please [read this](https://approov.io/docs/latest/approov-usage-documentation/#targetting-android-11-and-above) section of the reference documentation if targetting Android 11 (API level 30) or above.

## INITIALIZING APPROOVSERVICE
In order to use the `ApproovService` you must initialize it when your app is created, usually in the `onCreate` method:

```kotlin
import io.approov.service.retrofit.ApproovService

class YourApp: Application() {
    override fun onCreate() {
        super.onCreate()
        approovService = ApproovService(applicationContext, "<enter-your-config-string-here>")
    }

    companion object {
        lateinit var approovService: ApproovService
    }
}
```

The `<enter-your-config-string-here>` is a custom string that configures your Approov account access. This will have been provided in your Approov onboarding email.

This initializes Approov when the app is first created. A companion object allows other parts of the app to access the singleton Approov instance. All calls to `ApproovService` and the SDK itself are thread safe.

## USING APPROOVSERVICE
You can then modify your code that obtains a `RetrofitInstance` to make API calls as follows:

```kotlin
object ClientInstance {
    private static final String BASE_URL = "https://your.domain"
    private var retrofitBuilder: Retrofit.Builder? = null
    val retrofitInstance: Retrofit
        get() {
            if (retrofitBuilder == null) {
                retrofitBuilder = Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
            }
            return ShapesApp.approovService.getRetrofit(retrofitBuilder!!)
        }
}
```

This obtains a retrofit instance includes an `OkHttp` interceptor to add the `Approov-Token` header and pins the connections.

## CUSTOM OKHTTP BUILDER
By default, the Retrofit instance gets a default client constructed with a default `OkHttpClient`. However, your existing code may use a customized `OkHttpClient` with, for instance, different timeouts or other interceptors. For example, if you have existing code:

```kotlin
val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
val retrofit = retrofit2.Retrofit.Builder().baseUrl("https://your.domain/").client(client).build()
```
Pass the modified `OkHttp.Builder` to the `ApproovService` as follows:

```kotlin
YourApp.approovService.setOkHttpClientBuilder(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS))
val retrofitBuilder = retrofit2.Retrofit.Builder().baseUrl("https://your.domain/")
val retrofit = YourApp.approovService.getRetrofit(retrofitBuilder)
```

This call to `setOkHttpClientBuilder` only needs to be made once. Subsequent calls to `YourApp.approovService.getRetrofit()` will then always a `OkHttpClient` with the builder values included.

## CHECKING IT WORKS
Initially you won't have set which API domains to protect, so the interceptor will not add anything. It will have called Approov though and made contact with the Approov cloud service. You will see logging from Approov saying `UNKNOWN_URL`.

Your Approov onboarding email should contain a link allowing you to access [Live Metrics Graphs](https://approov.io/docs/latest/approov-usage-documentation/#metrics-graphs). After you've run your app with Approov integration you should be able to see the results in the live metrics within a minute or so. At this stage you could even release your app to get details of your app population and the attributes of the devices they are running upon.

However, to actually protect your APIs there are some further steps you can learn about in [Next Steps](https://github.com/approov/quickstart-android-kotlin-retrofit/blob/master/NEXT-STEPS.md).

