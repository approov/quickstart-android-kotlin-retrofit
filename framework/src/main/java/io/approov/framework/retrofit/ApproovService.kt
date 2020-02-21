// ApproovService framework for integrating Approov into apps using Retrofit.
//
// MIT License
// 
// Copyright (c) 2016-present, Critical Blue Ltd.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
// (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
// publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
// subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
// ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package io.approov.framework.retrofit

import android.content.Context
import android.util.Log
import com.criticalblue.approovsdk.Approov
import com.criticalblue.approovsdk.Approov.TokenFetchCallback
import com.criticalblue.approovsdk.Approov.TokenFetchResult
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import java.io.IOException
import java.util.*

// ApproovService provides a mediation layer to the Approov SDK itself
class ApproovService(
        private val appContext: Context, config: String?) {
    // true if the Approov SDK initialized okay
    private var initialized: Boolean = false
    // builder to be used for custom OkHttp clients
    private var okHttpBuilder: OkHttpClient.Builder
    // any header to be used for binding in Approov tokens or null if not set
    private var bindingHeader: String? = null
    // map of cached Retrofit instances keyed by their unique builders
    private var retrofitMap: MutableMap<Retrofit.Builder, Retrofit?>

    /**
     * Prefetches an Approov token in the background. The placeholder domain "www.approov.io" is
     * simply used to initiate the fetch and does not need to be a valid API for the account. This
     * method can be used to lower the effective latency of a subsequent token fetch by starting
     * the operation earlier so the subsequent fetch may be able to use a cached token.
     */
    @Synchronized
    fun prefetchApproovToken() {
        if (initialized)
            Approov.fetchApproovToken(PrefetchCallbackHandler(), "www.approov.io")
    }

    /**
     * Writes the latest dynamic configuration that the Approov SDK has. This clears the cached
     * Retrofit client since the pins may have changed and therefore a client rebuild is required.
     */
    @Synchronized
    fun updateDynamicConfig() {
        Log.i(TAG, "Approov dynamic configuration updated")
        putApproovDynamicConfig(Approov.fetchConfig())
        retrofitMap = HashMap()
    }

    /**
     * Stores an application's dynamic configuration string in non-volatile storage.
     *
     * The default implementation stores the string in shared preferences, and setting
     * the config string to null is equivalent to removing the config.
     *
     * @param config a configuration string
     */
    protected fun putApproovDynamicConfig(config: String?) {
        val prefs = appContext.getSharedPreferences(APPROOV_PREFS, 0)
        val editor = prefs.edit()
        editor.putString(APPROOV_CONFIG, config)
        editor.apply()
    }

    /**
     * Returns the application's dynamic configuration string from non-volatile storage.
     *
     * The default implementation retrieves the string from shared preferences.
     *
     * @return config string, or null if not present
     */
    protected val approovDynamicConfig: String?
        get() {
            val prefs = appContext.getSharedPreferences(APPROOV_PREFS, 0)
            return prefs.getString(APPROOV_CONFIG, null)
        }

    /**
     * Sets the OkHttpClient.Builder to be used for constructing the OkHttpClients used in the
     * Retrofit instances. This allows a custom configuration to be set, with additional interceptors
     * and properties. This clears the cached Retrofit client instances so should only be called when
     * an actual builder change is required.
     *
     * @param builder is the OkHttpClient.Builder to be used in Retrofit instances
     */
    @Synchronized
    fun setOkHttpClientBuilder(builder: OkHttpClient.Builder) {
        okHttpBuilder = builder
        retrofitMap = HashMap()
    }

    /**
     * Sets a binding header that must be present on all requests using the Approov service. A
     * header should be chosen whose value is unchanging for most requests (such as an
     * Authorization header). A hash of the header value is included in the issued Approov tokens
     * to bind them to the value. This may then be verified by the backend API integration. This
     * method should typically only be called once.
     *
     * @param header is the header to use for Approov token binding
     */
    @Synchronized
    fun setBindingHeader(header: String) {
        if (bindingHeader == null || bindingHeader != header) {
            bindingHeader = header
            retrofitMap = HashMap()
        }
    }

    /**
     * Gets a Retrofit instance that enables the Approov service. The builder for Retrofit should
     * be provided to allow its customization. This simply adds the underlying OkHttpClient to be
     * used. Approov tokens are added in headers to requests, and connections are also pinned.
     * Retrofit instances are added lazily on demand but are cached if there is no change.
     * lazily on demand but is cached if there are no changes. Note that once constructed and
     * passed to this method, Retrofit builder instances should not be changed further. If any
     * changes are required then a new builder should be constructed. Use "setOkHttpClientBuilder" to
     * provide any special properties for the underlying OkHttpClient.
     *
     * @param builder is the Retrofit.Builder for required client instance
     * @return Retrofit instance to be used with Approov
     */
    @Synchronized
    fun getRetrofit(builder: Retrofit.Builder): Retrofit? {
        var retrofit = retrofitMap[builder]
        if (retrofit == null) {
            // build any required OkHttpClient on demand
            val okHttpClient: OkHttpClient
            if (initialized) {
                // build the pinning configuration
                var pinBuilder = CertificatePinner.Builder()
                val pins = Approov.getPins("public-key-sha256")
                for ((key, value) in pins) {
                    for (pin in value) pinBuilder = pinBuilder.add(key!!, "sha256/$pin")
                }

                // remove any existing ApproovTokenInterceptor from the builder
                val interceptors = okHttpBuilder.interceptors()
                val iter = interceptors.iterator()
                while (iter.hasNext()) {
                    val interceptor = iter.next()
                    if (interceptor is ApproovTokenInterceptor) iter.remove()
                }

                // build the OkHttpClient with the correct pins preset and Approov interceptor
                Log.i(TAG, "Building new Approov OkHttpClient")
                okHttpClient = okHttpBuilder.certificatePinner(pinBuilder.build())
                        .addInterceptor(ApproovTokenInterceptor(this, bindingHeader))
                        .build()
            } else { // if the Approov SDK could not be initialized then we can't pin or add Approov tokens
                Log.e(TAG, "Cannot build Approov OkHttpClient due to initialization failure")
                okHttpClient = okHttpBuilder.build()
            }

            // build a new Retrofit instance
            retrofit = builder.client(okHttpClient).build()
            retrofitMap[builder] = retrofit
        }
        return retrofit
    }

    companion object {
        // logging tag
        private const val TAG = "ApproovFramework"

        // keys for the Approov shared preferences
        private const val APPROOV_CONFIG = "approov-config"
        private const val APPROOV_PREFS = "approov-prefs"
    }

    /**
     * Creates an Approov service.
     *
     * @param context the Application context
     * @param config the initial service config string
     */
    init {
        // setup ready for building Retrofit instances
        okHttpBuilder = OkHttpClient.Builder()
        retrofitMap = HashMap()

        // initialize the Approov SDK
        val dynamicConfig = approovDynamicConfig
        try {
            Approov.initialize(appContext, config, dynamicConfig, null)
            initialized = true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Approov initialization failed: " + e.message)
        }

        // if we didn't have a dynamic configuration (after the first launch on the app) then
        // we fetch the latest and write it to local storage now
        if (initialized && (dynamicConfig == null))
            updateDynamicConfig()
    }
}

/**
 * Callback handler for prefetching an Approov token. We simply log as we don't need the token
 * itself, as it will be returned as a cached value on a subsequent token fetch.
 */
internal class PrefetchCallbackHandler : TokenFetchCallback {
    override fun approovCallback(pResult: TokenFetchResult) {
        if (pResult.status == Approov.TokenFetchStatus.UNKNOWN_URL)
            Log.i(TAG, "Approov prefetch success")
        else
            Log.i(TAG, "Approov prefetch failure: " + pResult.status.toString())
    }

    companion object {
        // logging tag
        private const val TAG = "ApproovPrefetch"
    }
}

/*
 * Interceptor to add Approov tokens to requests and ensures connections are pinned.
 */
internal class ApproovTokenInterceptor
/**
 * Constructs an new interceptor that adds Approov tokens.
 *
 * @param service is the underlying ApproovService being used
 * @param header is any token binding header to use or null otherwise
 */
(
        private val approovService: ApproovService,
        private val bindingHeader: String?) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // update the data hash based on any token binding header
        var request = chain.request()
        if (bindingHeader != null) {
            if (!request.headers.names().contains(bindingHeader)) throw IOException("Approov missing token binding header: $bindingHeader")
            Approov.setDataHashInToken(request.header(bindingHeader))
        }

        // request an Approov token for the domain
        val host = request.url.host
        val approovResults = Approov.fetchApproovTokenAndWait(host)

        // provide information about the obtained token or error (note "approov token -check" can
        // be used to check the validity of the token and if you use token annotations they
        // will appear here to determine why a request is being rejected)
        Log.i(TAG, "Approov Token for " + host + ": " + approovResults.loggableToken)

        // update any dynamic configuration
        if (approovResults.isConfigChanged)
            approovService.updateDynamicConfig()

        // warn if we need to update the pins (this will be cleared by using getRetrofit
        // but will persist if the app fails to call this regularly)
        if (approovResults.isForceApplyPins)
            Log.e(TAG, "Approov Pins need to be updated")

        // check the status of Approov token fetch
        if (approovResults.status == Approov.TokenFetchStatus.SUCCESS) {
            // we successfully obtained a token so add it to the header for the request
            request = request.newBuilder().header(APPROOV_HEADER, APPROOV_TOKEN_PREFIX + approovResults.token).build()
        } else if (approovResults.status != Approov.TokenFetchStatus.NO_APPROOV_SERVICE &&
                approovResults.status != Approov.TokenFetchStatus.UNKNOWN_URL &&
                approovResults.status != Approov.TokenFetchStatus.UNPROTECTED_URL) {
            // we have failed to get an Approov token in such a way that there is no point in proceeding
            // with the request - generally a retry is needed, unless the error is permanent
            throw IOException("Approov token fetch failed: " + approovResults.status.toString())
        }

        // proceed with the rest of the chain
        return chain.proceed(request)
    }

    companion object {
        // logging tag
        private const val TAG = "ApproovInterceptor"
        // header that will be added to Approov enabled requests
        private const val APPROOV_HEADER = "Approov-Token"
        // any prefix to be added before the Approov token, such as "Bearer "
        private const val APPROOV_TOKEN_PREFIX = ""
    }
}
