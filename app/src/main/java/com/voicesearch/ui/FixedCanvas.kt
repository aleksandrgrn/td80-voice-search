package com.voicesearch.ui

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics

// Вёрстка рассчитана на экран проектора: 1280×720 dp. Приставки Android TV отдают
// 1920×1080 при density 2.0, то есть 960×540 dp, и туда она не помещается.
// Поэтому короткая сторона экрана у приложения всегда 720 dp.
private const val CANVAS_SHORT_SIDE_DP = 720

internal fun canvasDensityDpi(shortSidePx: Int): Int =
    shortSidePx * DisplayMetrics.DENSITY_DEFAULT / CANVAS_SHORT_SIDE_DP

fun Context.withFixedCanvas(): Context {
    val metrics = resources.displayMetrics
    val dpi = canvasDensityDpi(minOf(metrics.widthPixels, metrics.heightPixels))
    val config = Configuration(resources.configuration).apply {
        densityDpi = dpi
        screenWidthDp = metrics.widthPixels * DisplayMetrics.DENSITY_DEFAULT / dpi
        screenHeightDp = metrics.heightPixels * DisplayMetrics.DENSITY_DEFAULT / dpi
        smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp)
    }
    return createConfigurationContext(config)
}
