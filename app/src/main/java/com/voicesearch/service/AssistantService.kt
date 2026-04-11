package com.voicesearch.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.content.Intent
import android.util.Log
import com.voicesearch.ui.SearchActivity

class AssistantService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.d(TAG, "AssistantService connected")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_ASSIST && event.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "ASSIST key intercepted, launching SearchActivity")
            val intent = Intent(this, SearchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only intercept key events
    }

    override fun onInterrupt() {
        Log.w(TAG, "AssistantService interrupted")
    }

    companion object {
        private const val TAG = "VoiceSearch"
    }
}
