package com.marfeel.compass.cdp.store

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.marfeel.compass.cdp.CDP_MIRROR_TTL_MS
import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

class CdpMirrorStoreTest {
	private lateinit var prefs: MockSharedPreference
	private var now: Long = 1_000_000L
	private lateinit var store: CdpMirrorStore<List<String>>

	@Before
	fun setUp() {
		prefs = MockSharedPreference()
		store = CdpMirrorStore(
			prefs = prefs,
			prefix = "cdpsegs_",
			payloadKey = "segments",
			serialize = { list -> JsonArray().apply { list.forEach { add(it) } } },
			deserialize = { el -> el.asJsonArray.map { it.asString } },
			default = emptyList(),
			clock = { now }
		)
	}

	@Test
	fun `read returns default when account or masterId is falsy`() {
		store.write("acc", "mid", listOf("a"))
		assertEquals(emptyList<String>(), store.read(null, "mid"))
		assertEquals(emptyList<String>(), store.read("acc", null))
		assertEquals(emptyList<String>(), store.read("", "mid"))
	}

	@Test
	fun `write then read round-trips a fresh payload`() {
		store.write("acc", "mid", listOf("a", "b"))
		assertEquals(listOf("a", "b"), store.read("acc", "mid"))
	}

	@Test
	fun `read returns default once the payload is past the TTL`() {
		store.write("acc", "mid", listOf("a"))
		now += CDP_MIRROR_TTL_MS
		assertEquals(emptyList<String>(), store.read("acc", "mid"))
	}

	@Test
	fun `read returns default for a malformed envelope`() {
		prefs.edit().putString("cdpsegs_mid_acc", "not json").apply()
		assertEquals(emptyList<String>(), store.read("acc", "mid"))
	}

	@Test
	fun `write is a no-op for falsy account or masterId`() {
		store.write(null, "mid", listOf("a"))
		store.write("acc", "", listOf("a"))
		assertTrue(prefs.preferenceMap.isEmpty())
	}

	@Test
	fun `write adds the masterId to the index`() {
		store.write("acc", "mid1", listOf("a"))
		store.write("acc", "mid2", listOf("b"))
		val index = indexFor("acc")
		assertTrue(index.contains("mid1"))
		assertTrue(index.contains("mid2"))
	}

	@Test
	fun `clear nulls the data and removes from the index`() {
		store.write("acc", "mid", listOf("a"))
		store.clear("acc", "mid")
		assertEquals(emptyList<String>(), store.read("acc", "mid"))
		assertTrue(!indexFor("acc").contains("mid"))
	}

	@Test
	fun `active mid round-trips`() {
		store.setActiveMid("acc", "mid")
		assertEquals("mid", store.getActiveMid("acc"))
	}

	@Test
	fun `cleanupExpired purges stale entries but never the active mid`() {
		store.write("acc", "stale", listOf("a"))
		store.write("acc", "active", listOf("b"))
		store.setActiveMid("acc", "active")
		now += CDP_MIRROR_TTL_MS

		store.cleanupExpired("acc")

		val index = indexFor("acc")
		assertTrue(index.contains("active"))
		assertTrue(!index.contains("stale"))
	}

	@Test
	fun `cleanupExpired keeps fresh entries`() {
		store.write("acc", "fresh", listOf("a"))
		store.write("acc", "active", listOf("b"))
		store.setActiveMid("acc", "active")

		store.cleanupExpired("acc")

		val index = indexFor("acc")
		assertTrue(index.contains("fresh"))
		assertTrue(index.contains("active"))
	}

	private fun indexFor(account: String): List<String> {
		val raw = prefs.getString("cdpsegs_index_$account", "[]")!!
		return com.google.gson.JsonParser.parseString(raw).asJsonArray.map { it.asString }
	}
}
