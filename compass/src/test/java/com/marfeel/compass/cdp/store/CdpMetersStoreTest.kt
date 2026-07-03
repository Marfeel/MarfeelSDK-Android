package com.marfeel.compass.cdp.store

import com.marfeel.compass.cdp.model.MeterState
import com.marfeel.compass.cdp.model.MeterWindow
import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Date

class CdpMetersStoreTest {
	private lateinit var store: CdpMetersStore

	@Before
	fun setUp() {
		store = CdpMetersStore(MockSharedPreference())
	}

	@Test
	fun `meter with dates round-trips to the second`() {
		val started = Date(1_700_000_000_000L)
		val expires = Date(1_700_086_400_000L)
		store.write(
			"acc", "mid",
			listOf(
				MeterState(
					name = "paywall",
					count = 3,
					startedAt = started,
					expiresAt = expires,
					window = MeterWindow("calendar", "P1M", "Europe/Madrid")
				)
			)
		)

		val read = store.read("acc", "mid").single()
		assertEquals("paywall", read.name)
		assertEquals(3, read.count)
		assertEquals(started.time, read.startedAt?.time)
		assertEquals(expires.time, read.expiresAt?.time)
		assertEquals("calendar", read.window.duration)
		assertEquals("P1M", read.window.period)
		assertEquals("Europe/Madrid", read.window.tz)
	}

	@Test
	fun `optional threshold trio is preserved`() {
		store.write(
			"acc", "mid",
			listOf(MeterState(name = "p", count = 1, threshold = 5, reached = false, remaining = 4))
		)
		val read = store.read("acc", "mid").single()
		assertEquals(5, read.threshold)
		assertEquals(false, read.reached)
		assertEquals(4, read.remaining)
	}

	@Test
	fun `absent threshold trio stays absent`() {
		store.write("acc", "mid", listOf(MeterState(name = "views", count = 2)))
		val read = store.read("acc", "mid").single()
		assertNull(read.threshold)
		assertNull(read.reached)
		assertNull(read.remaining)
	}

	@Test
	fun `missing window defaults to empty strings`() {
		store.write("acc", "mid", listOf(MeterState(name = "p")))
		val read = store.read("acc", "mid").single()
		assertEquals("", read.window.duration)
		assertEquals("", read.window.period)
		assertEquals("", read.window.tz)
	}

	@Test
	fun `null dates round-trip as null`() {
		store.write("acc", "mid", listOf(MeterState(name = "p")))
		val read = store.read("acc", "mid").single()
		assertNull(read.startedAt)
		assertNull(read.expiresAt)
	}
}
