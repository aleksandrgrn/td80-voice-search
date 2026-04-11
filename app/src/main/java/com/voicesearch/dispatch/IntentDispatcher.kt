package com.voicesearch.dispatch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.voicesearch.model.TargetApp

object IntentDispatcher {

    private val TARGET_APPS = listOf(
        TargetApp("ru.yourok.num", Intent.ACTION_SEARCH, "NUM"),
        TargetApp("org.smarttube.stable", "android.media.action.MEDIA_PLAY_FROM_SEARCH", "SmartTube"),
        TargetApp("ru.yourok.lampa", Intent.ACTION_SEARCH, "Lampa"),
        TargetApp("com.laxymedia.deluxe", Intent.ACTION_SEARCH, "LazyMediaDeluxe"),
    )

    fun launch(context: Context, app: TargetApp, query: String): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo(app.packageName, 0)
            val intent = Intent(app.searchAction).apply {
                setPackage(app.packageName)
                putExtra(android.app.SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched ${app.displayName} with query: $query")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "${app.displayName} not installed: ${app.packageName}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${app.displayName}: ${e.message}")
            false
        }
    }

    fun getInstalledApps(context: Context): List<TargetApp> {
        val pm = context.packageManager
        return TARGET_APPS.filter { app ->
            try {
                pm.getPackageInfo(app.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private const val TAG = "VoiceSearch"
}
