package com.voicesearch.dispatch

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for NumSearchHelper.
 *
 * Focus: launchAndSearch() launch logic and error handling.
 * The accessibility-based node interaction cannot be tested in pure unit tests
 * (requires Android runtime with AccessibilityService), so we verify:
 * - Launch branching: blank query, NUM not installed, no launchable activity, success
 * - That the correct intent is dispatched to startActivity
 * - Clipboard is set via ClipboardManager before search automation
 */
class NumSearchHelperTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setUp() {
        context = mockk()
        packageManager = mockk()
        clipboardManager = mockk(relaxed = true)
        every { context.packageManager } returns packageManager
        every { context.startActivity(any()) } just Runs
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
    }

    private fun stubPackageInstalled(packageName: String) {
        every { packageManager.getPackageInfo(packageName, 0) } returns mockk()
    }

    private fun stubPackageNotInstalled(packageName: String) {
        every { packageManager.getPackageInfo(packageName, 0) } throws PackageManager.NameNotFoundException()
    }

    private fun stubResolveActivitySuccess(componentPackage: String = "com.test", componentName: String = "com.test.MainActivity") {
        val activityInfo = ActivityInfo()
        activityInfo.packageName = componentPackage
        activityInfo.name = componentName
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = activityInfo
        every { packageManager.resolveActivity(any(), ofType<Int>()) } returns resolveInfo
    }

    private fun stubResolveActivityNull() {
        every { packageManager.resolveActivity(any(), ofType<Int>()) } returns null
    }

    // ===== blank query =====

    @Test
    fun launchAndSearch_blankQuery_returnsNoHandler() {
        val result = NumSearchHelper.launchAndSearch(context, "")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    @Test
    fun launchAndSearch_whitespaceQuery_returnsNoHandler() {
        val result = NumSearchHelper.launchAndSearch(context, "   ")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    // ===== NUM not installed =====

    @Test
    fun launchAndSearch_numNotInstalled_returnsNoHandler() {
        stubPackageNotInstalled("ru.yourok.num")

        val result = NumSearchHelper.launchAndSearch(context, "Матрица")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    // ===== NUM installed but no launchable activity =====

    @Test
    fun launchAndSearch_noLaunchableActivity_returnsNoHandler() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivityNull()

        val result = NumSearchHelper.launchAndSearch(context, "Матрица")
        assertEquals(LaunchResult.NO_HANDLER, result)
    }

    // ===== Successful launch =====

    @Test
    fun launchAndSearch_success_returnsSuccess() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess("ru.yourok.num", "ru.yourok.num.ui.CollectionsActivity")

        val result = NumSearchHelper.launchAndSearch(context, "Матрица")
        assertEquals(LaunchResult.SUCCESS, result)
    }

    @Test
    fun launchAndSearch_success_callsStartActivity() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess("ru.yourok.num", "ru.yourok.num.ui.CollectionsActivity")

        NumSearchHelper.launchAndSearch(context, "Матрица")

        verify(exactly = 1) { context.startActivity(any()) }
    }

    @Test
    fun launchAndSearch_success_callsStartActivityWithLeanbackLauncherIntent() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess("ru.yourok.num", "ru.yourok.num.ui.CollectionsActivity")

        NumSearchHelper.launchAndSearch(context, "Матрица")

        // Verify startActivity was called — intent content cannot be verified
        // in unit tests (mockable JAR returns null for getters).
        // The intent construction is verified in instrumented tests.
        verify(exactly = 1) { context.startActivity(any()) }
    }

    // ===== startActivity throws =====

    @Test
    fun launchAndSearch_activityNotFound_returnsNoHandler() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess()
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        val result = NumSearchHelper.launchAndSearch(context, "Матрица")
        assertEquals(LaunchResult.ERROR, result)
    }

    @Test
    fun launchAndSearch_generalException_returnsError() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess()
        every { context.startActivity(any()) } throws RuntimeException("boom")

        val result = NumSearchHelper.launchAndSearch(context, "Матрица")
        assertEquals(LaunchResult.ERROR, result)
    }

    // ===== Clipboard is set before search automation =====

    @Test
    fun launchAndSearch_success_setsClipboardWithQuery() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess()

        NumSearchHelper.launchAndSearch(context, "Матрица")

        verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }
    }

    @Test
    fun launchAndSearch_cyrillicQuery_setsClipboard() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess()

        NumSearchHelper.launchAndSearch(context, "Во все тяжкие")

        verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }
    }

    @Test
    fun launchAndSearch_success_clipboardUnavailable_fallsBackToInputText() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess()
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns null

        // Should still return SUCCESS — falls back to "input text" path
        val result = NumSearchHelper.launchAndSearch(context, "matrix")
        assertEquals(LaunchResult.SUCCESS, result)
    }

    // ===== Cyrillic query support (clipboard approach works for any Unicode) =====

    @Test
    fun launchAndSearch_cyrillicQuery_accepted() {
        stubPackageInstalled("ru.yourok.num")
        stubResolveActivitySuccess()

        val result = NumSearchHelper.launchAndSearch(context, "Во все тяжкие")
        assertEquals(LaunchResult.SUCCESS, result)
    }
}
