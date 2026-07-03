package com.marfeel.compass.cdp.store

import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class CdpSegmentsStoreTest {
	private lateinit var store: CdpSegmentsStore

	@Before
	fun setUp() {
		store = CdpSegmentsStore(MockSharedPreference())
	}

	@Test
	fun `segments round-trip`() {
		store.write("acc", "mid", listOf("sports", "tech"))
		assertEquals(listOf("sports", "tech"), store.read("acc", "mid"))
	}

	@Test
	fun `read defaults to empty when nothing stored`() {
		assertEquals(emptyList<String>(), store.read("acc", "mid"))
	}

	@Test
	fun `clear empties the bucket`() {
		store.write("acc", "mid", listOf("a"))
		store.clear("acc", "mid")
		assertEquals(emptyList<String>(), store.read("acc", "mid"))
	}

	@Test
	fun `active mid round-trips`() {
		store.setActiveMid("acc", "mid")
		assertEquals("mid", store.getActiveMid("acc"))
	}
}
