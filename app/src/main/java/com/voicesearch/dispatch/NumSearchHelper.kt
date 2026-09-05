package com.voicesearch.dispatch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataOutputStream

object NumSearchHelper {

    private const val TAG = "VoiceSearch"
    private const val NUM_PACKAGE = "ru.yourok.num"

    // Coordinates for 1280x720
    private const val MENU_SEARCH_X = 109
    private const val MENU_SEARCH_Y = 183
    private const val SEARCH_FIELD_X = 702
    private const val SEARCH_FIELD_Y = 167
    private const val SEARCH_BUTTON_X = 904
    private const val SEARCH_BUTTON_Y = 281

    fun launchAndSearch(context: Context, query: String): LaunchResult {
        if (query.isBlank()) return LaunchResult.NO_HANDLER

        try {
            context.packageManager.getPackageInfo(NUM_PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "NUM not installed")
            return LaunchResult.NO_HANDLER
        }

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(NUM_PACKAGE)
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val resolved = context.packageManager.resolveActivity(mainIntent, 0)
        if (resolved == null) return LaunchResult.NO_HANDLER

        mainIntent.setComponent(ComponentName(
            resolved.activityInfo.packageName,
            resolved.activityInfo.name
        ))

        try {
            context.startActivity(mainIntent)
            Log.d(TAG, "NUM launched, starting su automation for query='$query'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch NUM", e)
            return LaunchResult.ERROR
        }

        // Run all automation in a single su process (survives app being killed)
        executeSuSearch(query)
        return LaunchResult.SUCCESS
    }

    /**
     * Execute the entire NUM search automation in a single `su` shell process.
     * This process runs as root and survives even if our app is OOM-killed.
     */
    private fun executeSuSearch(query: String) {
        // Escape single quotes for shell
        val escaped = query.replace("'", "'\\''")
        
        val script = """
            sleep 2.5
            input tap $MENU_SEARCH_X $MENU_SEARCH_Y
            sleep 0.5
            input keyevent 23
            sleep 1.5
            input tap $SEARCH_FIELD_X $SEARCH_FIELD_Y
            sleep 0.5
            input text '$escaped'
            sleep 1.0
            input tap $SEARCH_BUTTON_X $SEARCH_BUTTON_Y
        """.trimIndent()

        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes(script)
                os.writeBytes("\nexit 0\n")
                os.flush()
                os.close()
                val exitCode = process.waitFor()
                Log.d(TAG, "NUM su automation completed, exitCode=$exitCode")
            } catch (e: Exception) {
          Log.e(TAG, "NUM su automation failed", e)
            }
        }.start()
    }
}