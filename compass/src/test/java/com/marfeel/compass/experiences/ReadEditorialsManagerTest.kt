package com.marfeel.compass.experiences

import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

class ReadEditorialsManagerTest {
    private lateinit var prefs: MockSharedPreference
    private lateinit var manager: ReadEditorialsManager

    @Before
    fun setUp() {
        prefs = MockSharedPreference()
        manager = ReadEditorialsManager(prefs)
    }

    @Test
    fun `add stores editorial id`() {
        manager.add("123")
        assertEquals(listOf("123"), manager.getIds())
    }

    @Test
    fun `add deduplicates but refreshes ordering`() {
        manager.add("123")
        manager.add("456")
        manager.add("123")
        assertEquals(listOf("456", "123"), manager.getIds())
    }

    @Test
    fun `add ignores blank ids`() {
        manager.add("")
        manager.add("   ")
        assertTrue(manager.getIds().isEmpty())
    }

    @Test
    fun `buildRedParam returns empty when no ids`() {
        assertEquals("", manager.buildRedParam())
    }

    @Test
    fun `buildRedParam delta-encodes sorted ids`() {
        manager.add("130")
        manager.add("120")
        assertEquals("120,10", manager.buildRedParam())
    }

    @Test
    fun `buildRedParam skips non-numeric ids`() {
        manager.add("abc")
        manager.add("100")
        assertEquals("100", manager.buildRedParam())
    }

    @Test
    fun `clear removes stored ids`() {
        manager.add("1")
        manager.clear()
        assertTrue(manager.getIds().isEmpty())
    }

    @Test
    fun `caps at 100 entries FIFO`() {
        repeat(105) { manager.add((1000 + it).toString()) }
        val ids = manager.getIds()
        assertEquals(100, ids.size)
        assertEquals("1005", ids.first())
        assertEquals("1104", ids.last())
    }
}
