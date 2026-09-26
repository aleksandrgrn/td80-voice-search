package com.voicesearch.ui

import android.view.KeyEvent
import android.view.View

/**
 * Куда уводить фокус из однострочного поля ввода по стрелке, или null — стрелка двигает курсор.
 *
 * Пульт с USB-приёмником Android видит клавиатурой, а стрелки клавиатуры EditText
 * оставляет себе даже на краю текста: фокус из поля не уходил никуда.
 * Стрелки с модификаторами (Shift — выделение) остаются полю.
 */
fun edgeFocusDirection(
    keyCode: Int,
    selectionStart: Int,
    selectionEnd: Int,
    length: Int,
    hasModifiers: Boolean,
): Int? {
    if (hasModifiers) return null
    val collapsed = selectionStart == selectionEnd
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT.takeIf { collapsed && selectionStart == 0 }
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT.takeIf { collapsed && selectionEnd == length }
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }
}
