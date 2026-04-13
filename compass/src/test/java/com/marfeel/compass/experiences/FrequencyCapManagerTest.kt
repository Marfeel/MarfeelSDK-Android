package com.marfeel.compass.experiences

import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FrequencyCapManagerTest {
    private lateinit var prefs: MockSharedPreference
    private var now: Long = 0L
    private val utc = TimeZone.getTimeZone("UTC")
    private lateinit var manager: FrequencyCapManager

    private fun millisFor(year: Int, monthOneBased: Int, day: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance(utc)
        cal.clear()
        cal.set(year, monthOneBased - 1, day, hour, 0, 0)
        return cal.timeInMillis
    }

    @Before
    fun setUp() {
        prefs = MockSharedPreference()
        now = millisFor(2026, 4, 15)
        manager = FrequencyCapManager(prefs, clock = { now }, timeZone = utc)
    }

    @Test
    fun `trackImpression increments lifetime and day counts`() {
        manager.trackImpression("exp1")
        manager.trackImpression("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(2L, counts["l"])
        assertEquals(2L, counts["d"])
        assertEquals(2L, counts["w"])
        assertEquals(2L, counts["m"])
    }

    @Test
    fun `trackClose increments close counts`() {
        manager.trackClose("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(1L, counts["cl"])
        assertEquals(1L, counts["cd"])
        assertEquals(1L, counts["cw"])
        assertEquals(1L, counts["cm"])
    }

    @Test
    fun `ls is seconds since last impression`() {
        manager.trackImpression("exp1")
        now += 5_000L
        val counts = manager.getCounts("exp1")
        assertEquals(5L, counts["ls"])
    }

    @Test
    fun `cls is seconds since last close`() {
        manager.trackClose("exp1")
        now += 42_000L
        val counts = manager.getCounts("exp1")
        assertEquals(42L, counts["cls"])
    }

    @Test
    fun `day counter resets on calendar day change`() {
        manager.trackImpression("exp1")
        manager.trackImpression("exp1")
        now = millisFor(2026, 4, 16)
        manager.trackImpression("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(1L, counts["d"])
        assertEquals(3L, counts["l"])
    }

    @Test
    fun `week counter sums across days within iso week`() {
        now = millisFor(2026, 4, 13)
        manager.trackImpression("exp1")
        now = millisFor(2026, 4, 15)
        manager.trackImpression("exp1")
        now = millisFor(2026, 4, 19)
        manager.trackImpression("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(3L, counts["w"])
    }

    @Test
    fun `week counter rolls over when new iso week begins`() {
        now = millisFor(2026, 4, 19)
        manager.trackImpression("exp1")
        manager.trackImpression("exp1")
        now = millisFor(2026, 4, 20)
        manager.trackImpression("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(1L, counts["w"])
        assertEquals(3L, counts["l"])
    }

    @Test
    fun `month counter rolls over between months`() {
        manager.trackImpression("exp1")
        manager.trackImpression("exp1")
        now = millisFor(2026, 5, 1)
        manager.trackImpression("exp1")
        val counts = manager.getCounts("exp1")
        assertEquals(1L, counts["m"])
        assertEquals(3L, counts["l"])
    }

    @Test
    fun `buildUexp returns empty string when no data`() {
        assertEquals("", manager.buildUexp())
    }

    @Test
    fun `buildUexp emits all non-zero counters without config gating`() {
        manager.trackImpression("exp1")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("exp1"))
        assertTrue(uexp.contains("d|1"))
        assertTrue(uexp.contains("w|1"))
        assertTrue(uexp.contains("m|1"))
        assertTrue(uexp.contains("l|1"))
    }

    @Test
    fun `buildUexp omits zero-valued counters`() {
        manager.trackImpression("exp1")
        val uexp = manager.buildUexp()
        assertFalse(uexp.contains("cl|"))
        assertFalse(uexp.contains("cd|"))
        assertFalse(uexp.contains("cls|"))
    }

    @Test
    fun `buildUexp emits ls only when impression recorded`() {
        manager.trackClose("exp1")
        val uexp = manager.buildUexp()
        assertFalse(uexp.contains("ls|"))
        assertTrue(uexp.contains("cls|") || uexp.contains("cl|"))
    }

    @Test
    fun `buildUexp includes every experience in storage regardless of recency`() {
        manager.trackImpression("old")
        now += 30L * 24L * 3600L * 1000L
        manager.trackImpression("fresh")
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("old"))
        assertTrue(uexp.contains("fresh"))
    }

    @Test
    fun `applyResponseConfig prunes experiences not in config`() {
        manager.trackImpression("keep")
        manager.trackImpression("drop")
        manager.applyResponseConfig(mapOf("keep" to listOf("d")))
        val uexp = manager.buildUexp()
        assertTrue(uexp.contains("keep"))
        assertFalse(uexp.contains("drop"))
    }

    @Test
    fun `applyResponseConfig with empty config wipes all counters`() {
        manager.trackImpression("exp1")
        manager.trackImpression("exp2")
        manager.applyResponseConfig(emptyMap())
        assertEquals("", manager.buildUexp())
    }

    @Test
    fun `applyResponseConfig preserves counter state for retained experiences`() {
        manager.trackImpression("keep")
        manager.trackImpression("keep")
        manager.trackImpression("drop")
        manager.applyResponseConfig(mapOf("keep" to listOf("d")))
        val counts = manager.getCounts("keep")
        assertEquals(2L, counts["l"])
        assertEquals(2L, counts["d"])
    }

    @Test
    fun `clear wipes all counters`() {
        manager.trackImpression("exp1")
        manager.clear()
        assertEquals("", manager.buildUexp())
        assertEquals(0L, manager.getCounts("exp1")["l"])
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
    fun `getCounts returns zeros for unknown experience`() {
        val counts = manager.getCounts("unknown")
        assertEquals(0L, counts["l"])
        assertEquals(0L, counts["cl"])
        assertEquals(0L, counts["d"])
    }

    @Test
    fun `counters survive serialization round-trip`() {
        manager.trackImpression("exp1")
        manager.trackClose("exp1")
        val fresh = FrequencyCapManager(prefs, clock = { now }, timeZone = utc)
        val counts = fresh.getCounts("exp1")
        assertEquals(1L, counts["l"])
        assertEquals(1L, counts["cl"])
        assertEquals(1L, counts["d"])
        assertEquals(1L, counts["cd"])
    }
}
