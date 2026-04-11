package com.voicesearch

import android.app.Application
import android.util.Log

class VoiceSearchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceSearchApp initialized, TMDB key configured: ${BuildConfig.TMDB_API_KEY != "PLACEHOLDER"}")
    }

    companion object {
        private const val TAG = "VoiceSearch"
    }
}
