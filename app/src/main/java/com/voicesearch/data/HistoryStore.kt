package com.voicesearch.data

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.voicesearch.model.SearchResult

/**
 * Последние открытые карточки: новые сверху, без повторов, не больше [LIMIT].
 * Лишние вытесняются сами — удалять вручную не нужно.
 */
class HistoryStore(private val prefs: SharedPreferences) {

    private val adapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<SearchResult>>(
            Types.newParameterizedType(List::class.java, SearchResult::class.java)
        )

    fun all(): List<SearchResult> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(result: SearchResult) {
        val updated = (listOf(result) + all().filter { it.id != result.id }).take(LIMIT)
        prefs.edit().putString(KEY, adapter.toJson(updated)).apply()
    }

    companion object {
        const val LIMIT = 20
        private const val KEY = "history"
    }
}
