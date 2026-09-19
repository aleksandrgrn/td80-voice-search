package com.voicesearch

import android.app.Application
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VoiceSearchApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }

    companion object {
        /**
         * Shared OkHttpClient instance for the entire app.
         * Prevents connection pool leak when Activity is recreated.
         * Configured with extended timeouts for projector Wi-Fi.
         */
        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
