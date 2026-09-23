package com.marfeel.compass.cdp.store

import com.marfeel.compass.cdp.model.CdpConsentStatus
import com.marfeel.compass.cdp.model.CdpRememberedConsentDecision
import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class CdpConsentMemoryStoreTest {
	private val prefs = MockSharedPreference()
	private val store = CdpConsentMemoryStore(prefs)
	private val accepted3 = CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 10L)
	private val rejected1 = CdpRememberedConsentDecision("1", CdpConsentStatus.REJECTED, 20L)

	@Test
	fun `reads the per-account bucket under the local sentinel`() {
		store.remember("acc", "privacy", accepted3)
		assertTrue(prefs.contains("cdpconsents_local_acc"))
		assertEquals(accepted3, store.getRemembered("acc")["privacy"])
		assertTrue(store.getRemembered("other").isEmpty())
	}

	@Test
	fun `returns an empty map on a miss, a fresh copy each time`() {
		val first = store.getRemembered("acc")
		assertTrue(first.isEmpty())
		assertFalse(first === store.getRemembered("acc"))
	}

	@Test
	fun `remembering merges into the existing bucket`() {
		store.remember("acc", "privacy", accepted3)
		store.remember("acc", "newsletter", rejected1)
		assertEquals(setOf("privacy", "newsletter"), store.getRemembered("acc").keys)
	}

	@Test
	fun `a newer decision on the same consent replaces the older one`() {
		store.remember("acc", "privacy", accepted3)
		store.remember("acc", "privacy", rejected1)
		assertEquals(rejected1, store.getRemembered("acc")["privacy"])
		assertEquals(1, store.getRemembered("acc").size)
	}

	@Test
	fun `clearing drops the bucket`() {
		store.remember("acc", "privacy", accepted3)
		store.clear("acc")
		assertTrue(store.getRemembered("acc").isEmpty())
		assertFalse(prefs.contains("cdpconsents_local_acc"))
	}

	@Test
	fun `forgetting drops only the named decisions`() {
		store.remember("acc", "privacy", accepted3)
		store.remember("acc", "newsletter", rejected1)
		store.forget("acc", listOf("privacy"))
		assertEquals(setOf("newsletter"), store.getRemembered("acc").keys)
	}

	@Test
	fun `forgetting the last decision clears the bucket`() {
		store.remember("acc", "privacy", accepted3)
		store.forget("acc", listOf("privacy"))
		assertFalse(prefs.contains("cdpconsents_local_acc"))
	}

	@Test
	fun `forgetting an unknown decision writes nothing`() {
		store.remember("acc", "privacy", accepted3)
		val before = prefs.getString("cdpconsents_local_acc", null)
		store.forget("acc", listOf("ghost"))
		assertEquals(before, prefs.getString("cdpconsents_local_acc", null))
	}

	@Test
	fun `the wire status short form reads back normalised`() {
		prefs.edit().putString(
			"cdpconsents_local_acc",
			"""{"decisions":{"p":{"versionId":"2","status":"accept","ts":5}},"ts":${System.currentTimeMillis()}}"""
		).apply()
		assertEquals(CdpConsentStatus.ACCEPTED, store.getRemembered("acc")["p"]?.status)
	}
}
