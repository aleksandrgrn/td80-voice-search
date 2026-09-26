package com.voicesearch.dispatch

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import java.net.URLEncoder
import com.voicesearch.model.TargetApp

enum class LaunchResult {
    SUCCESS,
    NO_HANDLER,
    ERROR
}

object IntentDispatcher {

    const val PKG_NUM = "ru.yourok.num"
    const val PKG_SMARTTUBE = "org.smarttube.stable"
    const val PKG_LAMPA = "top.rootu.lamps"
    const val PKG_LAMPA_TWICKER = "ru.twicker.lampa"
    const val PKG_LAZYMEDIA = "com.lazycatsoftware.lmd"

    private val TARGET_APPS = listOf(
        TargetApp(PKG_NUM, Intent.ACTION_VIEW, "NUM"),
        TargetApp(PKG_SMARTTUBE, Intent.ACTION_VIEW, "SmartTube",
            dataUriTemplate = "https://www.youtube.com/results?search_query={query}"),
        TargetApp(PKG_LAMPA, Intent.ACTION_SEARCH, "Lampa"),
        TargetApp(PKG_LAMPA_TWICKER, Intent.ACTION_SEARCH, "Lampa"),
        // Неявный ACTION_SEARCH у LazyMedia резолвится в ActivityTvArticle, а та падает с NPE
        TargetApp(PKG_LAZYMEDIA, Intent.ACTION_SEARCH, "LazyMediaDeluxe",
            searchActivity = "com.lazycatsoftware.lazymediadeluxe.ui.tv.activities.ActivityTvSearch"),
    )

    /** Каталог этих приложений — TMDB: по ссылке themoviedb.org они открывают карточку. */
    val TMDB_CARD_PACKAGES = setOf(PKG_NUM, PKG_LAMPA, PKG_LAMPA_TWICKER)

    /** Две сборки Лампы под одной кнопкой; стоит обычно одна из них. */
    val LAMPA_PACKAGES = listOf(PKG_LAMPA, PKG_LAMPA_TWICKER)

    /** Test URI used by getSearchableApps() for NUM — NUM resolves ACTION_VIEW for TMDB URLs. */
    private const val NUM_TEST_URI = "https://www.themoviedb.org/movie/1"

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
            app.searchActivity?.let { setClassName(app.packageName, it) }
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            if (app.dataUriTemplate != null) {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val uriString = app.dataUriTemplate.replace("{query}", encodedQuery)
                data = Uri.parse(uriString)
            }
        }

        val resolveInfo = pm.resolveActivity(intent, 0)
        if (resolveInfo == null) {
            Log.w(TAG, "${app.displayName} cannot handle action: ${app.searchAction}")
            return LaunchResult.NO_HANDLER
        }

        intent.setComponent(ComponentName(
            resolveInfo.activityInfo.packageName,
            resolveInfo.activityInfo.name
        ))

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

    /**
     * Launch a target app, preferring a TMDB deep link when available.
     *
     * For NUM and Lampa: if tmdbId and tmdbType are provided, opens the specific movie/TV show
     * via ACTION_VIEW + TMDB URI. Otherwise falls through to [launch].
     *
     * For apps with dataUriTemplate (SmartTube): delegates to [launch] which
     * substitutes the query into the template.
     */
    fun launchWithTmdb(
        context: Context,
        app: TargetApp,
        query: String,
        tmdbId: String?,
        tmdbType: String?
    ): LaunchResult {
        if (query.isBlank()) return LaunchResult.NO_HANDLER

        // NUM / Lampa with TMDB info → deep link
        if (app.packageName in TMDB_CARD_PACKAGES && !tmdbId.isNullOrBlank() && !tmdbType.isNullOrBlank()) {
            val pm = context.packageManager

            try {
                pm.getPackageInfo(app.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "${app.displayName} not installed: ${app.packageName}")
                return LaunchResult.NO_HANDLER
            }

            // Сборка ru.twicker.lampa ссылок themoviedb.org не берёт — только свою схему.
            val tmdbUri = if (app.packageName == PKG_LAMPA_TWICKER) {
                Uri.parse("lampa://$PKG_LAMPA_TWICKER/${tmdbType}/${tmdbId}")
            } else {
                Uri.parse("https://www.themoviedb.org/${tmdbType}/${tmdbId}")
            }
            val intent = Intent(Intent.ACTION_VIEW, tmdbUri).apply {
                setPackage(app.packageName)
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val resolveInfo = pm.resolveActivity(intent, 0)
            if (resolveInfo == null) {
                Log.w(TAG, "${app.displayName} cannot handle TMDB URI: $tmdbUri")
                return LaunchResult.NO_HANDLER
            }

            intent.setComponent(ComponentName(
                resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name
            ))

            return try {
                context.startActivity(intent)
                Log.d(TAG, "Launched ${app.displayName} with TMDB URI: $tmdbUri")
                LaunchResult.SUCCESS
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Activity not found for ${app.displayName}: ${e.message}")
                LaunchResult.NO_HANDLER
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch ${app.displayName}: ${e.message}")
                LaunchResult.ERROR
            }
        }

        // Fall through to regular launch (SmartTube with dataUriTemplate, etc.)
        return launch(context, app, query)
    }

    fun getAllApps(): List<TargetApp> = TARGET_APPS

    fun getSearchableApps(context: Context): List<TargetApp> {
        val pm = context.packageManager
        return TARGET_APPS.filter { app ->
            try {
                pm.getPackageInfo(app.packageName, 0)
                val intent = Intent(app.searchAction).apply {
                    setPackage(app.packageName)
                    app.searchActivity?.let { setClassName(app.packageName, it) }
                    if (app.dataUriTemplate != null) {
                        val encodedQuery = URLEncoder.encode("test", "UTF-8")
                        val uriString = app.dataUriTemplate.replace("{query}", encodedQuery)
                        data = Uri.parse(uriString)
                    } else if (app.packageName == PKG_NUM) {
                        // NUM uses ACTION_VIEW with TMDB URI — test with a generic TMDB URI
                        data = Uri.parse(NUM_TEST_URI)
                    }
                }
                pm.resolveActivity(intent, 0) != null
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private const val TAG = "VoiceSearch"
}
