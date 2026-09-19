package com.voicesearch.data

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class ApiKeyStoreTest {

    private val prefs: SharedPreferences = mockk()
    private val editor: SharedPreferences.Editor = mockk()
    private val store = ApiKeyStore(prefs)

    @Test
    fun `get returns null when nothing stored`() {
        every { prefs.getString(any(), null) } returns null

        assertNull(store.get())
    }

    @Test
    fun `get returns null when stored value is blank`() {
        every { prefs.getString(any(), null) } returns "   "

        assertNull(store.get())
    }

    @Test
    fun `save writes the key and get reads it back`() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        store.save("abc")

        verify { editor.putString("tmdb_api_key", "abc") }
        verify { editor.apply() }

        every { prefs.getString(any(), null) } returns "abc"

        assertEquals("abc", store.get())
    }
}
