package com.voicesearch.ui

import android.text.InputFilter

/**
 * Ключ TMDB v3 — ровно 32 символа [0-9a-f]. Фильтр режет всё остальное и
 * не даёт полю вырасти длиннее ключа, на каком бы пути ни пришёл символ:
 * кнопка сетки, adb input text или цифровой блок пульта.
 */
object HexKeyFilter {

    const val MAX_LENGTH = 32

    /**
     * [source] — то, что пытаются вставить, [destLength] — текущая длина
     * поля, [replacedLength] — сколько символов поля вставка заменит.
     * Возвращает null, если source годится целиком: это контракт
     * InputFilter, «принять как есть». Иначе — то, что осталось после чистки
     * и обрезки.
     */
    fun keep(source: CharSequence, destLength: Int, replacedLength: Int): CharSequence? {
        val kept = StringBuilder(source.length)
        for (c in source) {
            if (c in '0'..'9' || c in 'a'..'f') kept.append(c)
        }
        val room = MAX_LENGTH - (destLength - replacedLength)
        if (room < kept.length) kept.setLength(maxOf(room, 0))
        return if (kept.length == source.length) null else kept.toString()
    }

    fun asInputFilter(): InputFilter = InputFilter { source, start, end, dest, dstart, dend ->
        keep(source.subSequence(start, end), dest.length, dend - dstart)
    }
}
