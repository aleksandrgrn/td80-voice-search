package com.voicesearch

import android.app.Application
import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VoiceSearchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceSearchApp initialized, TMDB key configured: ${BuildConfig.TMDB_API_KEY != "PLACEHOLDER"}")
    }

    companion object {
        private const val TAG = "VoiceSearch"

        /**
         * Shared OkHttpClient instance for the entire app.
         * Prevents connection pool leak when Activity is recreated.
         * Configured with extended timeouts for projector Wi-Fi.
         */
        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
