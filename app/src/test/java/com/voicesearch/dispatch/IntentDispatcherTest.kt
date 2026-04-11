package com.voicesearch.dispatch

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        every { packageManager.resolveActivity(any(), ofType<Int>()) } returns mockk()
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

    // ===== getAllApps() tests =====

    @Test
    fun getAllApps_returnsFourApps() {
        val apps = IntentDispatcher.getAllApps()
        assertEquals(4, apps.size)
    }

    @Test
    fun getAllApps_smartTubeHasMediaFocus() {
        val smartTube = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        assertEquals("movie", smartTube.mediaFocus)
    }

    @Test
    fun getAllApps_regularAppsHaveNoMediaFocus() {
        val regularApps = IntentDispatcher.getAllApps().filter { it.packageName != "org.smarttube.stable" }
        assertTrue("All non-SmartTube apps should have null mediaFocus",
            regularApps.all { it.mediaFocus == null })
    }

    @Test
    fun getAllApps_smartTubeUsesMediaPlayFromSearch() {
        val smartTube = IntentDispatcher.getAllApps().first { it.packageName == "org.smarttube.stable" }
        assertEquals("android.media.action.MEDIA_PLAY_FROM_SEARCH", smartTube.searchAction)
    }

    @Test
    fun getAllApps_otherAppsUseActionSearch() {
        val others = IntentDispatcher.getAllApps().filter { it.packageName != "org.smarttube.stable" }
        assertTrue("All non-SmartTube apps should use ACTION_SEARCH",
            others.all { it.searchAction == Intent.ACTION_SEARCH })
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
}
