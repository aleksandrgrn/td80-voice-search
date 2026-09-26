package com.voicesearch.ui

import android.view.KeyEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test

class EdgeFocusTest {

    private fun dir(keyCode: Int, start: Int, end: Int = start, length: Int = 4, modifiers: Boolean = false) =
        edgeFocusDirection(keyCode, start, end, length, modifiers)

    @Test
    fun `right at end of text leaves the field`() {
        assertEquals(View.FOCUS_RIGHT, dir(KeyEvent.KEYCODE_DPAD_RIGHT, 4))
    }

    @Test
    fun `right inside text moves the cursor`() {
        assertNull(dir(KeyEvent.KEYCODE_DPAD_RIGHT, 2))
    }

    @Test
    fun `left at start of text leaves the field`() {
        assertEquals(View.FOCUS_LEFT, dir(KeyEvent.KEYCODE_DPAD_LEFT, 0))
    }

    @Test
    fun `left inside text moves the cursor`() {
        assertNull(dir(KeyEvent.KEYCODE_DPAD_LEFT, 2))
    }

    @Test
    fun `empty field lets both sides go`() {
        assertEquals(View.FOCUS_LEFT, dir(KeyEvent.KEYCODE_DPAD_LEFT, 0, length = 0))
        assertEquals(View.FOCUS_RIGHT, dir(KeyEvent.KEYCODE_DPAD_RIGHT, 0, length = 0))
    }

    @Test
    fun `selection is not an edge`() {
        assertNull(dir(KeyEvent.KEYCODE_DPAD_RIGHT, 1, 4))
        assertNull(dir(KeyEvent.KEYCODE_DPAD_LEFT, 0, 3))
    }

    @Test
    fun `up and down always leave the single-line field`() {
        assertEquals(View.FOCUS_DOWN, dir(KeyEvent.KEYCODE_DPAD_DOWN, 2))
        assertEquals(View.FOCUS_UP, dir(KeyEvent.KEYCODE_DPAD_UP, 2))
    }

    @Test
    fun `arrows with modifiers stay with the field`() {
        assertNull(dir(KeyEvent.KEYCODE_DPAD_DOWN, 2, modifiers = true))
        assertNull(dir(KeyEvent.KEYCODE_DPAD_RIGHT, 4, modifiers = true))
    }

    @Test
    fun `other keys are not touched`() {
        assertNull(dir(KeyEvent.KEYCODE_A, 4))
        assertNull(dir(KeyEvent.KEYCODE_DPAD_CENTER, 4))
    }
}

class ScrollEdgeFocusTest {

    private fun dir(keyCode: Int, canUp: Boolean = false, canDown: Boolean = false, modifiers: Boolean = false) =
        scrollEdgeFocusDirection(keyCode, canUp, canDown, modifiers)

    @Test
    fun `down scrolls while there is text below, then leaves`() {
        assertNull(dir(KeyEvent.KEYCODE_DPAD_DOWN, canDown = true))
        assertEquals(View.FOCUS_DOWN, dir(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `up scrolls while there is text above, then leaves`() {
        assertNull(dir(KeyEvent.KEYCODE_DPAD_UP, canUp = true))
        assertEquals(View.FOCUS_UP, dir(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun `left and right always leave`() {
        assertEquals(View.FOCUS_LEFT, dir(KeyEvent.KEYCODE_DPAD_LEFT, canUp = true, canDown = true))
        assertEquals(View.FOCUS_RIGHT, dir(KeyEvent.KEYCODE_DPAD_RIGHT, canUp = true, canDown = true))
    }

    @Test
    fun `modifiers and other keys are not touched`() {
        assertNull(dir(KeyEvent.KEYCODE_DPAD_DOWN, modifiers = true))
        assertNull(dir(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
