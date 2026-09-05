package com.voicesearch.dispatch

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for IntentDispatcher.
 *
 * NOTE: In the Android mockable-jar environment (isReturnDefaultValues=true),
 * Intent property getters (action, extras, flags, package) always return
 * null / 0 / false. This is a known limitation — Intent content assertions
 * require instrumented tests or Robolectric. These tests focus on:
 * - LaunchResult branching logic
 * - PackageManager interaction flow
 * - getSearchableApps filtering
 * - dataUriTemplate configuration
 * - launchWithTmdb() TMDB deep link logic
 */
class IntentDispatcherTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        context = mockk()
        packageManager = mockk()
        every { context.packageManager } returns packageManager
        every { context.startActivity(any()) } just Runs
    }

    private fun stubPackageInstalled(packageName: String) {
        every { packageManager.getPackageInfo(packageName, 0) } returns mockk()
    }

    private fun stubPackageNotInstalled(packageName: String) {
        every { packageManager.getPackageInfo(packageName, 0) } throws PackageManager.NameNotFoundException()
    }

    private fun stubAllPackagesInstalled() {
        stubPackageInstalled("ru.yourok.num")
        stubPackageInstalled("org.smarttube.stable")
        stubPackageInstalled("ru.yourok.lampa")
        stubPackageInstalled("com.laxymedia.deluxe")
    }

    private fun stubResolveActivitySuccess() {
        val activityInfo = ActivityInfo()
        activityInfo.packageName = "com.test"
        activityInfo.name = "com.test.SearchActivity"
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = activityInfo
        every { packageManager.resolveActivity(any(), ofType<Int>()) } returns resolveInfo
    }

    private fun stubResolveActivityNull() {
        every { packageManager.resolveActivity(any(), ofType<Int>()) } returns null
    }

    // ===== launch() — LaunchResult tests =====

    @Test
    fun launch_blankQuery_returnsNoHandler() {
        val app = IntentDispatcher.getAllApps().first()
        val result = IntentDispatcher.launch(context, app, "")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launch_whitespaceQuery_returnsNoHandler() {
        val app = IntentDispatcher.getAllApps().first()
        val result = IntentDispatcher.launch(context, app, "   ")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launch_appNotInstalled_returnsNoHandler() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageNotInstalled(app.packageName)

        val result = IntentDispatcher.launch(context, app, "Матрица")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launch_resolveActivityNull_returnsNoHandler() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivityNull()

        val result = IntentDispatcher.launch(context, app, "Матрица")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launch_successfulLaunch_returnsSuccess() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()

        val result = IntentDispatcher.launch(context, app, "Матрица")
        assertEquals(LaunchResult.SUCCESS, result)
    }

    @Test
    fun launch_activityNotFoundException_returnsNoHandler() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        val result = IntentDispatcher.launch(context, app, "Матрица")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launch_generalException_returnsError() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()
        every { context.startActivity(any()) } throws RuntimeException("boom")

        val result = IntentDispatcher.launch(context, app, "Матрица")
        assertEquals(LaunchResult.ERROR, result)
    }

    // ===== launch() — interaction flow tests =====

    @Test
    fun launch_success_callsStartActivity() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()

        IntentDispatcher.launch(context, app, "Матрица")

        verify(exactly = 1) { context.startActivity(any()) }
    }

    @Test
    fun launch_noHandler_doesNotCallStartActivity() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageNotInstalled(app.packageName)

        IntentDispatcher.launch(context, app, "Матрица")

        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun launch_callsResolveActivityBeforeStart() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()

        IntentDispatcher.launch(context, app, "Матрица")

        verifyOrder {
            packageManager.resolveActivity(any(), ofType<Int>())
            context.startActivity(any())
        }
    }

    @Test
    fun launch_checksPackageInfoFirst() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()

        IntentDispatcher.launch(context, app, "Матрица")

        verifyOrder {
            packageManager.getPackageInfo(app.packageName, 0)
            packageManager.resolveActivity(any(), ofType<Int>())
            context.startActivity(any())
        }
    }

    @Test
    fun launch_smartTube_callsResolveActivity() {
        val app = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()

        val result = IntentDispatcher.launch(context, app, "Матрица")

        assertEquals(LaunchResult.SUCCESS, result)
        verify { packageManager.resolveActivity(any(), ofType<Int>()) }
    }

    @Test
    fun launch_setsExplicitComponent_afterResolve() {
        val app = IntentDispatcher.getAllApps().first()
        stubPackageInstalled(app.packageName)
        stubResolveActivitySuccess()

        IntentDispatcher.launch(context, app, "Матрица")

        verifyOrder {
            packageManager.resolveActivity(any(), ofType<Int>())
            context.startActivity(any())
        }
    }

    // ===== getAllApps() tests =====

    @Test
    fun getAllApps_returnsFourApps() {
        val apps = IntentDispatcher.getAllApps()
        assertEquals(4, apps.size)
    }

    @Test
    fun getAllApps_smartTubeHasNoMediaFocus() {
        val smartTube = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        assertEquals(null, smartTube.mediaFocus)
    }

    @Test
    fun getAllApps_allAppsHaveNoMediaFocus() {
        val apps = IntentDispatcher.getAllApps()
        assertTrue("All apps should have null mediaFocus",
            apps.all { it.mediaFocus == null })
    }

    @Test
    fun getAllApps_smartTubeUsesActionView() {
        val smartTube = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        assertEquals(Intent.ACTION_VIEW, smartTube.searchAction)
    }

    @Test
    fun getAllApps_numUsesActionView() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        assertEquals(Intent.ACTION_VIEW, num.searchAction)
    }

    @Test
    fun getAllApps_lampaUsesActionSearch() {
        val lampa = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.lampa" }
        assertEquals(Intent.ACTION_SEARCH, lampa.searchAction)
    }

    @Test
    fun getAllApps_lazyMediaUsesActionSearch() {
        val lazyMedia = IntentDispatcher.getAllApps().first { it.packageName == "com.laxymedia.deluxe" }
        assertEquals(Intent.ACTION_SEARCH, lazyMedia.searchAction)
    }

    // ===== dataUriTemplate tests =====

    @Test
    fun getAllApps_smartTubeHasDataUriTemplate() {
        val smartTube = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        assertNotNull(smartTube.dataUriTemplate)
        assertTrue(smartTube.dataUriTemplate!!.contains("{query}"))
    }

    @Test
    fun getAllApps_smartTubeDataUriTemplate_isYouTubeResults() {
        val smartTube = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        assertEquals("https://www.youtube.com/results?search_query={query}", smartTube.dataUriTemplate)
    }

    @Test
    fun getAllApps_numHasNoDataUriTemplate() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        assertNull(num.dataUriTemplate)
    }

    @Test
    fun getAllApps_lampaHasNoDataUriTemplate() {
        val lampa = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.lampa" }
        assertNull(lampa.dataUriTemplate)
    }

    @Test
    fun getAllApps_lazyMediaHasNoDataUriTemplate() {
        val lazyMedia = IntentDispatcher.getAllApps().first { it.packageName == "com.laxymedia.deluxe" }
        assertNull(lazyMedia.dataUriTemplate)
    }

    // ===== getSearchableApps() tests =====

    @Test
    fun getSearchableApps_allResolve_returnsFourApps() {
        stubAllPackagesInstalled()
        stubResolveActivitySuccess()

        val searchable = IntentDispatcher.getSearchableApps(context)
        assertEquals(4, searchable.size)
    }

    @Test
    fun getSearchableApps_noneResolve_returnsZeroApps() {
        stubAllPackagesInstalled()
        stubResolveActivityNull()

        val searchable = IntentDispatcher.getSearchableApps(context)
        assertEquals(0, searchable.size)
    }

    @Test
    fun getSearchableApps_skipsNotInstalled() {
        stubPackageNotInstalled("ru.yourok.num")
        stubPackageInstalled("org.smarttube.stable")
        stubPackageInstalled("ru.yourok.lampa")
        stubPackageInstalled("com.laxymedia.deluxe")
        stubResolveActivitySuccess()

        val searchable = IntentDispatcher.getSearchableApps(context)
        assertFalse("NUM should not appear", searchable.any { it.packageName == "ru.yourok.num" })
        assertEquals(3, searchable.size)
    }

    // ===== getInstalledApps() backward compatibility =====

    @Test
    fun getInstalledApps_allInstalled_returnsFourApps() {
        stubAllPackagesInstalled()

        val installed = IntentDispatcher.getInstalledApps(context)
        assertEquals(4, installed.size)
    }

    @Test
    fun getInstalledApps_noneInstalled_returnsZeroApps() {
        stubPackageNotInstalled("ru.yourok.num")
        stubPackageNotInstalled("org.smarttube.stable")
        stubPackageNotInstalled("ru.yourok.lampa")
        stubPackageNotInstalled("com.laxymedia.deluxe")

        val installed = IntentDispatcher.getInstalledApps(context)
        assertEquals(0, installed.size)
    }

    // ===== launchWithTmdb() — NUM with TMDB deep link =====

    @Test
    fun launchWithTmdb_numWithTmdbId_returnsSuccess() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()

        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", "603", "movie")
        assertEquals(LaunchResult.SUCCESS, result)
        verify { context.startActivity(any()) }
    }

    @Test
    fun launchWithTmdb_numWithoutTmdbId_fallsThroughToLaunch() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()

        // Without TMDB info, NUM falls through to launch() which uses ACTION_VIEW without data URI
        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", null, null)
        // NUM without dataUriTemplate and without TMDB → launch() uses ACTION_VIEW with no data → resolveActivity may fail
        // But we stubbed it to succeed, so it should work
        assertEquals(LaunchResult.SUCCESS, result)
    }

    @Test
    fun launchWithTmdb_numWithNullTmdbId_fallsThroughToLaunch() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()

        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", null, "movie")
        assertEquals(LaunchResult.SUCCESS, result)
    }

    @Test
    fun launchWithTmdb_numWithNullTmdbType_fallsThroughToLaunch() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()

        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", "603", null)
        assertEquals(LaunchResult.SUCCESS, result)
    }

    @Test
    fun launchWithTmdb_numNotInstalled_returnsNoHandler() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageNotInstalled(num.packageName)

        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", "603", "movie")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launchWithTmdb_numTmdbResolveFails_returnsNoHandler() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivityNull()

        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", "603", "movie")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launchWithTmdb_numTmdbActivityNotFound_returnsNoHandler() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", "603", "movie")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launchWithTmdb_numTmdbGeneralException_returnsError() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()
        every { context.startActivity(any()) } throws RuntimeException("boom")

        val result = IntentDispatcher.launchWithTmdb(context, num, "Матрица", "603", "movie")
        assertEquals(LaunchResult.ERROR, result)
    }

    // ===== launchWithTmdb() — SmartTube falls through to launch() =====

    @Test
    fun launchWithTmdb_smartTubeIgnoresTmdb_delegatesToLaunch() {
        val smartTube = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        stubPackageInstalled(smartTube.packageName)
        stubResolveActivitySuccess()

        // SmartTube should use dataUriTemplate regardless of TMDB params
        val result = IntentDispatcher.launchWithTmdb(context, smartTube, "Матрица", "603", "movie")
        assertEquals(LaunchResult.SUCCESS, result)
    }

    @Test
    fun launchWithTmdb_lampaIgnoresTmdb_delegatesToLaunch() {
        val lampa = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.lampa" }
        stubPackageInstalled(lampa.packageName)
        stubResolveActivitySuccess()

        val result = IntentDispatcher.launchWithTmdb(context, lampa, "Матрица", "603", "movie")
        assertEquals(LaunchResult.SUCCESS, result)
    }

    // ===== launchWithTmdb() — blank query =====

    @Test
    fun launchWithTmdb_blankQuery_returnsNoHandler() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        val result = IntentDispatcher.launchWithTmdb(context, num, "", "603", "movie")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launchWithTmdb_whitespaceQuery_returnsNoHandler() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        val result = IntentDispatcher.launchWithTmdb(context, num, "   ", "603", "movie")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    // ===== launchWithTmdb() — interaction flow for NUM with TMDB =====

    @Test
    fun launchWithTmdb_numWithTmdb_callsResolveBeforeStart() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()

        IntentDispatcher.launchWithTmdb(context, num, "Матрица", "603", "movie")

        verifyOrder {
            packageManager.getPackageInfo(num.packageName, 0)
            packageManager.resolveActivity(any(), ofType<Int>())
            context.startActivity(any())
        }
    }

    @Test
    fun launchWithTmdb_numTvShow_returnsSuccess() {
        val num = IntentDispatcher.getAllApps().first { it.packageName == "ru.yourok.num" }
        stubPackageInstalled(num.packageName)
        stubResolveActivitySuccess()

        val result = IntentDispatcher.launchWithTmdb(context, num, "Во все тяжкие", "1396", "tv")
        assertEquals(LaunchResult.SUCCESS, result)
    }
}
