package com.voicesearch.ui

import org.junit.Assert.*
import org.junit.Test

class HexKeyFilterTest {

    @Test
    fun `keeps a full valid key as is`() {
        assertNull(HexKeyFilter.keep("0123456789abcdef0123456789abcdef", 0, 0))
    }

    @Test
    fun `strips uppercase and punctuation`() {
        assertEquals("a1f", HexKeyFilter.keep("aZ1!f", 0, 0).toString())
    }

    @Test
    fun `rejects everything when field is full`() {
        assertEquals("", HexKeyFilter.keep("a", 32, 0).toString())
    }

    @Test
    fun `truncates to the remaining room`() {
        assertEquals("ab", HexKeyFilter.keep("abcdef", 30, 0).toString())
    }

    @Test
    fun `counts replaced characters as freed room`() {
        assertEquals("abcd", HexKeyFilter.keep("abcdef", 32, 4).toString())
    }
}
