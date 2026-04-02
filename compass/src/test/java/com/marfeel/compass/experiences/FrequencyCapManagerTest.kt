package com.marfeel.compass.experiences

import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

class FrequencyCapManagerTest {
    private lateinit var prefs: MockSharedPreference
    private lateinit var manager: FrequencyCapManager

    @Before
    fun setUp() {
        prefs = MockSharedPreference()
        manager = FrequencyCapManager(prefs)
    }

    @Test
    fun `trackImpression increments lifetime and day counts`() {
        manager.trackImpression("exp1")
        manager.trackImpression("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(2L, counts["l"])
        assertEquals(2L, counts["d"])
    }

    @Test
    fun `trackClose increments close counts`() {
        manager.trackClose("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(1L, counts["cl"])
        assertEquals(1L, counts["cd"])
    }

    @Test
    fun `trackImpression updates last seen timestamp`() {
        manager.trackImpression("exp1")
        val counts = manager.getCounts("exp1")
        assertTrue((counts["ls"] ?: 0L) > 0L)
    }

    @Test
    fun `buildUexp returns empty string when no data`() {
        val uexp = manager.buildUexp()
        assertEquals("", uexp)
    }

    @Test
    fun `buildUexp includes experience with recent impressions`() {
        manager.trackImpression("exp1")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("exp1"))
        assertTrue(uexp.contains("l|1"))
    }

    @Test
    fun `buildUexp uses semicolon separator between experiences`() {
        manager.trackImpression("exp1")
        manager.trackImpression("exp2")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains(";"))
        assertFalse(uexp.startsWith(";"))
        assertFalse(uexp.endsWith(";"))
    }

    @Test
    fun `buildUexp includes experience requested by server config`() {
        manager.updateFrequencyCapConfig(mapOf("exp_server" to listOf("d")))
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("exp_server"))
    }

    @Test
    fun `updateFrequencyCapConfig persists config`() {
        val config = mapOf("exp1" to listOf("d", "ls"), "exp2" to listOf("w"))
        manager.updateFrequencyCapConfig(config)
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("exp1"))
        assertTrue(uexp.contains("exp2"))
    }

    @Test
    fun `getCounts returns zeros for unknown experience`() {
        val counts = manager.getCounts("unknown")
        assertEquals(0L, counts["l"])
        assertEquals(0L, counts["cl"])
    }
}
