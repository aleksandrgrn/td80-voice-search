package com.voicesearch

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.voicesearch.service.AssistantService
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VoiceSearchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceSearchApp initialized, TMDB key configured: ${BuildConfig.TMDB_API_KEY != "PLACEHOLDER"}")

        // Логируем статус AccessibilityService при запуске приложения
        logAccessibilityServiceStatus()
    }

    private fun logAccessibilityServiceStatus() {
        val isRunning = AssistantService.isRunning
        Log.i(TAG, "AssistantService.isRunning = $isRunning")

        val am = getSystemService(AccessibilityManager::class.java)
        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            val ourServiceEnabled = enabledServices.any {
                it.id.startsWith("$packageName/")
            }
            Log.i(TAG, "AccessibilityManager reports our service enabled = $ourServiceEnabled " +
                    "(total generic services: ${enabledServices.size})")
        } else {
            Log.w(TAG, "AccessibilityManager not available")
        }
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
