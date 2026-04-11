package com.voicesearch.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.voicesearch.BuildConfig
import com.voicesearch.ui.SearchActivity

class AssistantService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceSearch"
        private const val DEBOUNCE_MS = 600L
        const val EXTRA_FROM_ASSIST_KEY = "from_assist_key"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var lastLaunchTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        // P0 FIX: Обязательно присвоение — иначе setServiceInfo() не вызовется
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }

        isRunning = true
        Log.i(TAG, "AssistantService connected, key filtering enabled")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(null)

        val keyCode = event.keyCode
        val action = event.action

        // Debug-логирование ВСЕХ key events для диагностики реального пульта
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onKeyEvent: keyCode=$keyCode action=$action " +
                    "label=${KeyEvent.keyCodeToString(keyCode)} " +
                    "scanCode=${event.scanCode} source=${event.source}")
        }

        val isAssistKey = keyCode == KeyEvent.KEYCODE_ASSIST           // 219
        val isVoiceAssistKey = keyCode == KeyEvent.KEYCODE_VOICE_ASSIST  // 231

        // Логируем ACTION_UP для ASSIST-клавиш (диагностика)
        if (action == KeyEvent.ACTION_UP && (isAssistKey || isVoiceAssistKey)) {
            Log.d(TAG, "ASSIST key ACTION_UP (keyCode=$keyCode)")
            return true  // consume — парный к ACTION_DOWN
        }

        // Обрабатываем только ACTION_DOWN
        if (action != KeyEvent.ACTION_DOWN) {
            return false
        }

        if (isAssistKey || isVoiceAssistKey) {
            // Debounce через SystemClock.elapsedRealtime()
            val now = SystemClock.elapsedRealtime()
            if (now - lastLaunchTime < DEBOUNCE_MS) {
                Log.d(TAG, "ASSIST key debounced (${now - lastLaunchTime}ms)")
                return true // consume, но не запускаем повторно
            }
            lastLaunchTime = now

            Log.i(TAG, "ASSIST key intercepted (keyCode=$keyCode), launching SearchActivity")
            launchSearchActivity()
            return true // consume event
        }

        // Все остальные кнопки — пропускаем
        return false
        }

    private fun launchSearchActivity() {
        try {
            val intent = Intent(this, SearchActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(EXTRA_FROM_ASSIST_KEY, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SearchActivity", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Не используется — мы только перехватываем key events
    }

    override fun onInterrupt() {
        Log.w(TAG, "AssistantService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        Log.i(TAG, "AssistantService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.w(TAG, "AssistantService destroyed")
    }
}
