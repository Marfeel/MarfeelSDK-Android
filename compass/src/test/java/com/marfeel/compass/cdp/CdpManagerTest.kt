package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpResolveParams
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.cdp.store.CdpSegmentsStore
import com.marfeel.compass.storage.MockSharedPreference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

internal class CdpManagerTest {
	private lateinit var api: CdpApiClient
	private lateinit var segmentsStore: CdpSegmentsStore
	private lateinit var manager: CdpManager

	private var enabled = true
	private var consent: Boolean? = null
	private var account: String? = "456"
	private var masterId: String? = null
	private var sessionId = "session-1"
	private var cached: CdpCachedIdentity? = null
	private var cachedSession: String? = null

	private val validUuid = "550e8400-e29b-41d4-a716-446655440000"
	private val mergedUuid = "550e8400-e29b-41d4-a716-446655440001"

	@Before
	fun setUp() {
		api = mockk()
		segmentsStore = CdpSegmentsStore(MockSharedPreference())
		masterId = null
		cached = null
		cachedSession = null
		sessionId = "session-1"
		consent = null
		enabled = true

		manager = CdpManager(
			isEnabled = { enabled },
			api = api,
			accountId = { account },
			getMasterId = { masterId },
			writeMasterId = { new -> val old = masterId; masterId = new; old },
			getUserId = { "cookie-1" },
			getCachedIdentity = { s -> if (s == cachedSession) cached else null },
			setCachedIdentity = { rfv, cohorts, s ->
				cached = CdpCachedIdentity(rfv, cohorts); cachedSession = s
			},
			getConsent = { consent },
			getSessionId = { sessionId }
		, segmentsStore = segmentsStore)
	}

	private fun resolveReturns(response: CdpIdentityResponse) {
		coEvery { api.resolve(any()) } returns response
	}

	@Test
	fun `resolve memoizes within a session`() = runBlocking {
		resolveReturns(CdpIdentityResponse(validUuid, CdpRfv(1, 1, 1, 1), listOf(1)))
		manager.resolveIdentity()
		manager.resolveIdentity()
		coVerify(exactly = 1) { api.resolve(any()) }
	}

	@Test
	fun `resolve skips network when identity is already known`() = runBlocking {
		masterId = validUuid
		cached = CdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(1))
		cachedSession = sessionId
		manager.resolveIdentity()
		coVerify(exactly = 0) { api.resolve(any()) }
	}

	@Test
	fun `resolve retries on failure (no master_id clears memo)`() = runBlocking {
		resolveReturns(UNKNOWN_CDP_IDENTITY)
		manager.resolveIdentity()
		manager.resolveIdentity()
		coVerify(exactly = 2) { api.resolve(any()) }
	}

	@Test
	fun `cached identity without master_id still re-resolves`() = runBlocking {
		cached = CdpCachedIdentity(null, emptyList())
		cachedSession = sessionId
		resolveReturns(CdpIdentityResponse(validUuid, null, emptyList()))
		manager.resolveIdentity()
		coVerify(exactly = 1) { api.resolve(any()) }
	}

	@Test
	fun `session rotation clears the memo and re-resolves`() = runBlocking {
		resolveReturns(CdpIdentityResponse(validUuid, null, emptyList()))
		manager.resolveIdentity()
		sessionId = "session-2"
		manager.resolveIdentity()
		coVerify(exactly = 2) { api.resolve(any()) }
	}

	@Test
	fun `no network without consent`() = runBlocking {
		consent = false
		resolveReturns(UNKNOWN_CDP_IDENTITY)
		manager.resolveIdentity()
		coVerify(exactly = 0) { api.resolve(any()) }
	}

	@Test
	fun `updateState caches rfv and cohorts even without a master_id`() = runBlocking {
		resolveReturns(CdpIdentityResponse(null, CdpRfv(9, 1, 2, 3), listOf(5, 6)))
		manager.resolveIdentity()
		assertNull(masterId)
		assertEquals(9, cached?.rfv?.rfv)
		assertEquals(listOf(5, 6), cached?.cohorts)
	}

	@Test
	fun `link awaits resolve then adopts the returned master_id`() = runBlocking {
		resolveReturns(CdpIdentityResponse(validUuid, null, emptyList()))
		coEvery { api.link(any()) } returns CdpIdentityResponse(mergedUuid, null, emptyList())

		manager.linkIdentity("registered_user_id", "u@x.com", isDeterministic = true)

		coVerify(exactly = 1) { api.resolve(any()) }
		val params = slot<CdpLinkParams>()
		coVerify { api.link(capture(params)) }
		assertEquals("registered_user_id", params.captured.idType)
		assertEquals(mergedUuid, masterId)
	}

	@Test
	fun `updateProfile stringifies and no-ops without a master_id`() = runBlocking {
		// without master_id → no-op
		manager.updateProfile(listOf("k" to "v"))
		coVerify(exactly = 0) { api.update(any()) }

		// with master_id → posts properties
		masterId = validUuid
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY
		manager.updateProfile(listOf("timezone" to "Europe/Madrid"))
		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals("Europe/Madrid", params.captured.properties?.get("timezone"))
	}

	@Test
	fun `segments are written locally first then synced`() = runBlocking {
		consent = true
		masterId = validUuid
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY

		manager.addSegment("sports")

		assertEquals(listOf("sports"), segmentsStore.read(account, validUuid))
		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals(listOf("sports"), params.captured.segmentsAdd)
	}

	@Test
	fun `pre-consent segments use the local bucket and are not synced`() = runBlocking {
		consent = null // default-allow, but no master_id yet
		manager.addSegment("tech")
		assertEquals(listOf("tech"), segmentsStore.read(account, LOCAL_MID_SENTINEL))
		coVerify(exactly = 0) { api.update(any()) }
	}

	@Test
	fun `replaceSegments diffs against the previous local snapshot`() = runBlocking {
		consent = true
		masterId = validUuid
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY
		segmentsStore.write(account, validUuid, listOf("a", "b"))

		manager.replaceSegments(listOf("b", "c", "c"))

		assertEquals(listOf("b", "c"), segmentsStore.read(account, validUuid))
		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals(listOf("c"), params.captured.segmentsAdd)
		assertEquals(listOf("a"), params.captured.segmentsRemove)
	}

	@Test
	fun `reconcile pushes segments_add only`() = runBlocking {
		consent = true
		masterId = validUuid
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY
		segmentsStore.write(account, validUuid, listOf("x", "y"))

		manager.reconcileSegments()

		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals(listOf("x", "y"), params.captured.segmentsAdd)
		assertNull(params.captured.segmentsRemove)
	}

	@Test
	fun `carry-over unions the local bucket into the new master_id and clears the old`() {
		segmentsStore.write(account, LOCAL_MID_SENTINEL, listOf("pre1", "pre2"))

		manager.transferCdpSegments(account, oldId = null, newId = validUuid)

		assertEquals(listOf("pre1", "pre2"), segmentsStore.read(account, validUuid))
		assertEquals(emptyList<String>(), segmentsStore.read(account, LOCAL_MID_SENTINEL))
		assertEquals(validUuid, segmentsStore.getActiveMid(account))
	}

	@Test
	fun `one-shot fires once when identity becomes ready`() = runBlocking {
		var fires = 0
		manager.onIdentityResolved { fires++ }
		assertEquals(0, fires) // not ready yet (no master_id)

		consent = true
		resolveReturns(CdpIdentityResponse(validUuid, null, emptyList()))
		manager.resolveIdentity()
		assertEquals(1, fires)

		manager.resolveIdentity() // already latched this session
		assertEquals(1, fires)
	}

	@Test
	fun `getData returns empty triple when disabled`() {
		enabled = false
		val data = manager.getData(serialized = true)
		assertNull(data.masterId)
		assertNull(data.rfv)
		assertTrue(data.cohorts.isEmpty())
	}

	@Test
	fun `getData serializes rfv and cohorts`() {
		masterId = validUuid
		cached = CdpCachedIdentity(CdpRfv(42, 3, 5, 7), listOf(101, 204))
		cachedSession = sessionId
		val data = manager.getData(serialized = true)
		assertEquals(validUuid, data.masterId)
		assertTrue(data.rfvSerialized.contains("\"rfv\":42"))
		assertEquals("[101,204]", data.cohortsSerialized)
	}
}
