package com.voicesearch.data

import android.content.SharedPreferences

/**
 * Ключ TMDB, введённый пользователем. Приватное хранилище приложения.
 * Принимает SharedPreferences, а не Context, чтобы проверяться на JVM.
 */
class ApiKeyStore(private val prefs: SharedPreferences) {

    /** Сохранённый ключ или null, если его нет или он пустой. */
    fun get(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(key: String) {
        prefs.edit().putString(KEY, key).apply()
    }

    companion object {
        const val PREFS_NAME = "voice_search"
        private const val KEY = "tmdb_api_key"
    }
}
