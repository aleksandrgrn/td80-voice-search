package com.voicesearch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedCanvasTest {

    @Test
    fun `projector 720p at density 1 keeps its density`() {
        assertEquals(160, canvasDensityDpi(720))
    }

    @Test
    fun `android tv box 1080p renders as 720 dp tall`() {
        assertEquals(240, canvasDensityDpi(1080))
    }

    @Test
    fun `4k screen renders as 720 dp tall`() {
        assertEquals(480, canvasDensityDpi(2160))
    }
}
