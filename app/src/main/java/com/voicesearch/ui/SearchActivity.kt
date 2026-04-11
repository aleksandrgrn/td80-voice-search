package com.voicesearch.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.voicesearch.R
import com.voicesearch.service.AssistantService

class SearchActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceSearch"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Логируем источник запуска
        val fromAssistKey = intent?.getBooleanExtra(AssistantService.EXTRA_FROM_ASSIST_KEY, false) ?: false
        Log.i(TAG, "SearchActivity launched, fromAssistKey=$fromAssistKey")

        // Проверяем статус AccessibilityService
        checkAccessibilityServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityServiceStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val fromAssistKey = intent?.getBooleanExtra(AssistantService.EXTRA_FROM_ASSIST_KEY, false) ?: false
        Log.i(TAG, "SearchActivity re-launched via onNewIntent, fromAssistKey=$fromAssistKey")
        // TODO: перезапустить голосовой поиск при fromAssistKey=true (задача 5)
    }

    private fun checkAccessibilityServiceStatus() {
        // Быстрая проверка через статическую переменную
        val isRunning = AssistantService.isRunning

        // Fallback через AccessibilityManager — более надёжная проверка
        val isEnabledViaManager = checkEnabledViaAccessibilityManager()

        val serviceEnabled = isRunning || isEnabledViaManager

        Log.i(TAG, "AccessibilityService status: isRunning=$isRunning, " +
                "managerEnabled=$isEnabledViaManager, result=$serviceEnabled")

        updateServiceStatusText(serviceEnabled)

        if (!serviceEnabled) {
            showAccessibilityNotEnabledDialog()
        }
    }

    private fun checkEnabledViaAccessibilityManager(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any { it.id.startsWith("$packageName/") }
    }

    private fun updateServiceStatusText(enabled: Boolean) {
        val statusText = findViewById<TextView>(R.id.serviceStatusText) ?: return
        if (enabled) {
            statusText.text = getString(R.string.accessibility_service_active)
            statusText.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            statusText.text = getString(R.string.accessibility_service_not_enabled)
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
        }
    }

    private fun showAccessibilityNotEnabledDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_not_enabled_title)
            .setMessage(R.string.accessibility_not_enabled_message)
            .setPositiveButton(R.string.accessibility_open_settings) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open accessibility settings", e)
                }
            }
            .setNegativeButton(R.string.accessibility_skip, null)
            .setCancelable(true)
            .show()
    }
}
