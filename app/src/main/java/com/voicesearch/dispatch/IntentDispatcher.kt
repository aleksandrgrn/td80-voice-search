package com.voicesearch.dispatch

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.voicesearch.model.TargetApp

enum class LaunchResult {
    SUCCESS,
    NO_HANDLER,
    ERROR
}

object IntentDispatcher {

    private val TARGET_APPS = listOf(
        TargetApp("ru.yourok.num", Intent.ACTION_SEARCH, "NUM"),
        TargetApp("org.smarttube.stable", "android.media.action.MEDIA_PLAY_FROM_SEARCH", "SmartTube", mediaFocus = "movie"),
        TargetApp("ru.yourok.lampa", Intent.ACTION_SEARCH, "Lampa"),
        TargetApp("com.laxymedia.deluxe", Intent.ACTION_SEARCH, "LazyMediaDeluxe"),
    )

    fun launch(context: Context, app: TargetApp, query: String): LaunchResult {
        if (query.isBlank()) return LaunchResult.NO_HANDLER

        val pm = context.packageManager

        try {
            pm.getPackageInfo(app.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "${app.displayName} not installed: ${app.packageName}")
            return LaunchResult.NO_HANDLER
        }

        val intent = Intent(app.searchAction).apply {
            setPackage(app.packageName)
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            app.mediaFocus?.let { putExtra(EXTRA_MEDIA_FOCUS, it) }
        }

        val resolveInfo = pm.resolveActivity(intent, 0)
        if (resolveInfo == null) {
            Log.w(TAG, "${app.displayName} cannot handle search action: ${app.searchAction}")
            return LaunchResult.NO_HANDLER
        }

        return try {
            context.startActivity(intent)
            Log.d(TAG, "Launched ${app.displayName} with query: $query")
            LaunchResult.SUCCESS
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Activity not found for ${app.displayName}: ${e.message}")
            LaunchResult.NO_HANDLER
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${app.displayName}: ${e.message}")
            LaunchResult.ERROR
        }
    }

    fun getAllApps(): List<TargetApp> = TARGET_APPS

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

    fun getSearchableApps(context: Context): List<TargetApp> {
        val pm = context.packageManager
        return TARGET_APPS.filter { app ->
            try {
                pm.getPackageInfo(app.packageName, 0)
                val intent = Intent(app.searchAction).apply {
                    setPackage(app.packageName)
                    app.mediaFocus?.let { putExtra(EXTRA_MEDIA_FOCUS, it) }
                }
                pm.resolveActivity(intent, 0) != null
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private const val TAG = "VoiceSearch"
    private const val EXTRA_MEDIA_FOCUS = "android.intent.extra.MEDIA_FOCUS"
}
