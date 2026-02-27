package com.astutebody.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `round-trip empty list`() {
        val original = emptyList<String>()
        val json = converters.fromStringList(original)
        val result = converters.toStringList(json)
        assertEquals(original, result)
    }

    @Test
    fun `round-trip single item`() {
        val original = listOf("chest")
        val json = converters.fromStringList(original)
        val result = converters.toStringList(json)
        assertEquals(original, result)
    }

    @Test
    fun `round-trip multiple items`() {
        val original = listOf("quadriceps", "glutes", "abductors", "calves")
        val json = converters.fromStringList(original)
        val result = converters.toStringList(json)
        assertEquals(original, result)
    }

    @Test
    fun `round-trip preserves special characters`() {
        val original = listOf("e-z curl bar", "body only", "middle back")
        val json = converters.fromStringList(original)
        val result = converters.toStringList(json)
        assertEquals(original, result)
    }

    @Test
    fun `fromStringList produces valid JSON`() {
        val json = converters.fromStringList(listOf("a", "b"))
        assertEquals("""["a","b"]""", json)
    }
}
