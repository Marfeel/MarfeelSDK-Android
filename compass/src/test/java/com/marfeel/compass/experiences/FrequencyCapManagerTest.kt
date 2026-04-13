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
    fun `trackClose updates close last seen timestamp`() {
        manager.trackClose("exp1")
        val counts = manager.getCounts("exp1")
        assertTrue((counts["cls"] ?: 0L) > 0L)
    }

    @Test
    fun `buildUexp returns empty string when no data`() {
        val uexp = manager.buildUexp()
        assertEquals("", uexp)
    }

    @Test
    fun `buildUexp filters counter keys by config`() {
        manager.updateFrequencyCapConfig(mapOf("exp1" to listOf("d")))
        manager.trackImpression("exp1")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("exp1,d|1"))
        assertFalse(uexp.contains("l|"))
        assertFalse(uexp.contains("m|"))
        assertFalse(uexp.contains("w|"))
    }

    @Test
    fun `buildUexp uses default freqs for recent activity without config`() {
        manager.trackImpression("exp1")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("exp1"))
        assertTrue(uexp.contains("d|1"))
        assertTrue(uexp.contains("l|1"))
        assertTrue(uexp.contains("m|1"))
        assertTrue(uexp.contains("w|1"))
        assertTrue(uexp.contains("ls|"))
    }

    @Test
    fun `buildUexp omits zero-valued counters`() {
        manager.updateFrequencyCapConfig(mapOf("exp1" to listOf("l", "cl", "d")))
        manager.trackImpression("exp1")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("l|1"))
        assertTrue(uexp.contains("d|1"))
        assertFalse(uexp.contains("cl|"))
    }

    @Test
    fun `buildUexp drops experiences with no non-zero counters`() {
        manager.updateFrequencyCapConfig(mapOf("exp_empty" to listOf("d")))
        val uexp = manager.buildUexp()
        assertFalse(uexp.contains("exp_empty"))
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
    fun `buildUexp includes cls when trackClose was called and config lists it`() {
        manager.updateFrequencyCapConfig(mapOf("exp1" to listOf("cl", "cls")))
        manager.trackClose("exp1")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("cl|1"))
        assertTrue(uexp.contains("cls|"))
    }

    @Test
    fun `getCounts returns zeros for unknown experience`() {
        val counts = manager.getCounts("unknown")
        assertEquals(0L, counts["l"])
        assertEquals(0L, counts["cl"])
        assertEquals(0L, counts["cls"])
    }
}
