package com.voicesearch

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.voicesearch.service.AssistantService

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
    }
}
