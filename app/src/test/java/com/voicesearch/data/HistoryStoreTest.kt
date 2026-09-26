package com.voicesearch.data

import android.content.SharedPreferences
import com.voicesearch.model.SearchResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.*
import org.junit.Test

class HistoryStoreTest {

    // SharedPreferences в памяти: store пишет строку, читает её же обратно.
    private var stored: String? = null
    private val prefs: SharedPreferences = mockk {
        every { getString(any(), null) } answers { stored }
        every { edit() } returns mockk {
            val value = slot<String>()
            every { putString(any(), capture(value)) } answers { stored = value.captured; self as SharedPreferences.Editor }
            every { apply() } returns Unit
        }
    }
    private val store = HistoryStore(prefs)

    private fun result(n: Int) = SearchResult(
        id = "tmdb_tv_$n",
        title = "Сериал $n",
        posterUrl = "https://image.tmdb.org/t/p/w342/$n.jpg",
        year = "2024",
        overview = "Описание $n",
        metadata = mapOf("tmdbId" to "$n", "type" to "tv", "rating" to "8.1"),
    )

    @Test
    fun `empty when nothing stored`() {
        assertEquals(emptyList<SearchResult>(), store.all())
    }

    @Test
    fun `newest first and all fields survive the round trip`() {
        store.add(result(1))
        store.add(result(2))

        assertEquals(listOf(result(2), result(1)), store.all())
    }

    @Test
    fun `reopened title moves to top without duplicate`() {
        store.add(result(1))
        store.add(result(2))
        store.add(result(1))

        assertEquals(listOf("tmdb_tv_1", "tmdb_tv_2"), store.all().map { it.id })
    }

    @Test
    fun `keeps only the latest 20`() {
        (1..25).forEach { store.add(result(it)) }

        val ids = store.all().map { it.id }
        assertEquals(20, ids.size)
        assertEquals("tmdb_tv_25", ids.first())
        assertEquals("tmdb_tv_6", ids.last())
    }

    @Test
    fun `corrupt data reads as empty`() {
        stored = "not json"

        assertEquals(emptyList<SearchResult>(), store.all())
    }
}
