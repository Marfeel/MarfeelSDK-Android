package com.marfeel.compass.cdp.store

import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class CdpServerSegmentsStoreTest {
	private val prefs = MockSharedPreference()
	private val store = CdpServerSegmentsStore(prefs)

	@Test
	fun `a miss reads as null, distinct from an empty list`() {
		assertNull(store.read("acc", "mid"))
		store.write("acc", "mid", emptyList())
		assertEquals(emptyList<String>(), store.read("acc", "mid"))
	}

	@Test
	fun `segments round-trip under the cdpsrvsegs prefix`() {
		store.write("acc", "mid", listOf("a", "b"))
		assertEquals(listOf("a", "b"), store.read("acc", "mid"))
		assertEquals(true, prefs.contains("cdpsrvsegs_mid_acc"))
	}

	@Test
	fun `clear drops the bucket back to a miss`() {
		store.write("acc", "mid", listOf("a"))
		store.clear("acc", "mid")
		assertNull(store.read("acc", "mid"))
	}

	@Test
	fun `cleanupExpired never purges the active master passed by the caller`() {
		store.write("acc", "active", listOf("a"))
		store.write("acc", "other", listOf("b"))
		store.cleanupExpired("acc", "active")
		assertEquals(listOf("a"), store.read("acc", "active"))
		assertEquals(listOf("b"), store.read("acc", "other"))
	}
}

class CdpServerPropertiesStoreTest {
	private val prefs = MockSharedPreference()
	private val store = CdpServerPropertiesStore(prefs)

	@Test
	fun `a miss reads as null, distinct from an empty map`() {
		assertNull(store.read("acc", "mid"))
		store.write("acc", "mid", emptyMap())
		assertEquals(emptyMap<String, String>(), store.read("acc", "mid"))
	}

	@Test
	fun `properties round-trip under the cdpsrvprops prefix preserving order`() {
		store.write("acc", "mid", linkedMapOf("plan" to "premium", "city" to "Barcelona"))
		assertEquals(listOf("plan", "city"), store.read("acc", "mid")!!.keys.toList())
		assertEquals("Barcelona", store.read("acc", "mid")!!["city"])
		assertEquals(true, prefs.contains("cdpsrvprops_mid_acc"))
	}

	@Test
	fun `a non-string payload value is coerced on read`() {
		prefs.edit().putString("cdpsrvprops_mid_acc", """{"properties":{"age":42,"vip":true},"ts":${System.currentTimeMillis()}}""").apply()
		assertEquals(mapOf("age" to "42", "vip" to "true"), store.read("acc", "mid"))
	}

	@Test
	fun `clear drops the bucket`() {
		store.write("acc", "mid", mapOf("a" to "b"))
		store.clear("acc", "mid")
		assertNull(store.read("acc", "mid"))
	}
}
