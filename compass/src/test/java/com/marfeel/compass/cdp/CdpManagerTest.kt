package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpDeleteParams
import com.marfeel.compass.cdp.model.CdpDeleteResponse
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpRememberedConsentDecision
import com.marfeel.compass.cdp.model.CdpConsentStatus
import com.marfeel.compass.cdp.model.CdpRfv
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

internal class CdpManagerTest {
	private lateinit var env: CdpTestEnv
	private val manager get() = env.manager
	private val api get() = env.api
	private val account get() = env.account

	@Before
	fun setUp() {
		env = CdpTestEnv()
	}

	private fun resolveReturns(response: CdpIdentityResponse) {
		coEvery { api.resolve(any()) } returns response
	}

	// region resolve

	@Test
	fun `resolve memoizes within a session`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, CdpRfv(1, 1, 1, 1), listOf(1)))
		manager.resolveIdentity()
		manager.resolveIdentity()
		coVerify(exactly = 1) { api.resolve(any()) }
	}

	@Test
	fun `resolve skips network when identity and both mirrors are already known`() = runBlocking {
		env.warmVisitor(segments = emptyList(), properties = emptyMap())
		manager.resolveIdentity()
		coVerify(exactly = 0) { api.resolve(any()) }
	}

	@Test
	fun `resolve guard - resolves when the server-segment mirror has no entry`() = runBlocking {
		env.masterId = UUID_A
		env.cached = CdpCachedIdentity(null, emptyList())
		env.cachedSession = env.sessionId
		env.serverPropertiesStore.write(account, UUID_A, emptyMap())
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList(), segments = emptyList()))

		manager.resolveIdentity()

		coVerify(exactly = 1) { api.resolve(any()) }
	}

	@Test
	fun `resolve guard - re-resolves when the properties mirror is a miss but the segments are warm`() = runBlocking {
		env.masterId = UUID_A
		env.cached = CdpCachedIdentity(null, emptyList())
		env.cachedSession = env.sessionId
		env.serverSegmentsStore.write(account, UUID_A, listOf("s"))
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))

		manager.resolveIdentity()

		coVerify(exactly = 1) { api.resolve(any()) }
	}

	@Test
	fun `resolve guard - cache hit commits the stored mirrors`() = runBlocking {
		env.warmVisitor(segments = listOf("lv_a_user_CC_LV"), properties = mapOf("plan" to "premium"))

		manager.resolveIdentity()

		assertEquals(listOf("lv_a_user_CC_LV"), manager.serverSegments)
		assertEquals(mapOf("plan" to "premium"), manager.serverProperties)
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
		env.cached = CdpCachedIdentity(null, emptyList())
		env.cachedSession = env.sessionId
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		manager.resolveIdentity()
		coVerify(exactly = 1) { api.resolve(any()) }
	}

	@Test
	fun `session rotation clears the memo and re-resolves`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		manager.resolveIdentity()
		env.sessionId = "session-2"
		manager.resolveIdentity()
		coVerify(exactly = 2) { api.resolve(any()) }
	}

	@Test
	fun `no network without consent`() = runBlocking {
		env.consent = false
		resolveReturns(UNKNOWN_CDP_IDENTITY)
		manager.resolveIdentity()
		coVerify(exactly = 0) { api.resolve(any()) }
	}

	@Test
	fun `updateState caches rfv and cohorts even without a master_id`() = runBlocking {
		resolveReturns(CdpIdentityResponse(null, CdpRfv(9, 1, 2, 3), listOf(5, 6)))
		manager.resolveIdentity()
		assertNull(env.masterId)
		assertEquals(9, env.cached?.rfv?.rfv)
		assertEquals(listOf(5, 6), env.cached?.cohorts)
	}

	// endregion

	// region link

	@Test
	fun `link awaits resolve then adopts the returned master_id`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		coEvery { api.link(any()) } returns CdpIdentityResponse(UUID_B, null, emptyList())

		manager.linkIdentity("registered_user_id", "u@x.com", isDeterministic = true)

		coVerify(exactly = 1) { api.resolve(any()) }
		val params = slot<CdpLinkParams>()
		coVerify { api.link(capture(params)) }
		assertEquals("registered_user_id", params.captured.idType)
		assertEquals(UUID_B, env.masterId)
	}

	// endregion

	// region identityFresh

	@Test
	fun `identityFresh starts false`() {
		assertFalse(manager.identityFresh)
		assertFalse(manager.getUserProfile().identityFresh)
	}

	@Test
	fun `identityFresh flips true after a resolve that returned a master_id`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		manager.resolveIdentity()
		assertTrue(manager.identityFresh)
		assertTrue(manager.getUserProfile(serialized = true).identityFresh)
	}

	@Test
	fun `identityFresh stays false when the resolve failed`() = runBlocking {
		resolveReturns(UNKNOWN_CDP_IDENTITY)
		manager.resolveIdentity()
		assertFalse(manager.identityFresh)
	}

	@Test
	fun `identityFresh stays false on the warm path, which never round-trips`() = runBlocking {
		env.warmVisitor()
		manager.resolveIdentity()
		assertFalse(manager.identityFresh)
	}

	@Test
	fun `identityFresh flips true after a link`() = runBlocking {
		env.consent = true
		resolveReturns(UNKNOWN_CDP_IDENTITY)
		coEvery { api.link(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList())
		manager.linkIdentity("email", "u@x.com", true)
		assertTrue(manager.identityFresh)
	}

	@Test
	fun `identityFresh is dropped by clearIdentity`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		manager.resolveIdentity()
		manager.clearIdentity()
		assertFalse(manager.identityFresh)
	}

	// endregion

	// region server segments

	@Test
	fun `syncServerSegments stores server segments minus device-owned ones`() {
		env.masterId = UUID_A
		env.ownedSegments = listOf("evolok:Device_mobile")

		manager.syncServerSegments(listOf("evolok:Device_mobile", "lv_a_user_CC_LV", "is_deterministic"))

		assertEquals(listOf("lv_a_user_CC_LV", "is_deterministic"), manager.serverSegments)
		assertEquals(listOf("lv_a_user_CC_LV", "is_deterministic"), env.serverSegmentsStore.read(account, UUID_A))
	}

	@Test
	fun `syncServerSegments also treats cdpsegs entries as device-owned`() {
		env.masterId = UUID_A
		env.segmentsStore.write(account, UUID_A, listOf("lv_a_user_CC_LV"))

		manager.syncServerSegments(listOf("lv_a_user_CC_LV", "is_deterministic"))

		assertEquals(listOf("is_deterministic"), manager.serverSegments)
	}

	@Test
	fun `syncServerSegments treats a missing segments field as an empty server set`() {
		env.masterId = UUID_A
		manager.syncServerSegments(null)
		assertEquals(emptyList<String>(), manager.serverSegments)
		assertEquals(emptyList<String>(), env.serverSegmentsStore.read(account, UUID_A))
	}

	@Test
	fun `syncServerSegments no-ops without a master_id`() {
		manager.syncServerSegments(listOf("lv_a_user_CC_LV"))
		assertEquals(emptyList<String>(), manager.serverSegments)
		assertNull(env.serverSegmentsStore.read(account, LOCAL_MID_SENTINEL))
	}

	@Test
	fun `a resolve persists the segments from the response`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList(), segments = listOf("srv")))
		manager.resolveIdentity()
		assertEquals(listOf("srv"), manager.serverSegments)
		assertEquals(listOf("srv"), env.serverSegmentsStore.read(account, UUID_A))
	}

	@Test
	fun `removeSegment prunes the key from the server diff`() = runBlocking {
		env.consent = true
		env.masterId = UUID_A
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY
		manager.syncServerSegments(listOf("lv_a_user_CC_LV", "is_deterministic"))

		manager.removeSegment("lv_a_user_CC_LV")

		assertEquals(listOf("is_deterministic"), manager.serverSegments)
		assertEquals(listOf("is_deterministic"), env.serverSegmentsStore.read(account, UUID_A))
	}

	@Test
	fun `clearSegments empties the server diff even when nothing is locally owned`() = runBlocking {
		env.consent = true
		env.masterId = UUID_A
		manager.syncServerSegments(listOf("lv_a_user_CC_LV"))

		manager.clearSegments()

		assertEquals(emptyList<String>(), manager.serverSegments)
		coVerify(exactly = 0) { api.update(any()) }
	}

	@Test
	fun `server segments are dropped, never carried over, on a master_id change`() = runBlocking {
		env.consent = true
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList(), segments = listOf("old-srv")))
		manager.resolveIdentity()
		coEvery { api.link(any()) } returns CdpIdentityResponse(UUID_B, null, emptyList(), segments = listOf("new-srv"))

		manager.linkIdentity("email", "u@x.com", true)

		assertNull(env.serverSegmentsStore.read(account, UUID_A))
		assertEquals(listOf("new-srv"), env.serverSegmentsStore.read(account, UUID_B))
		assertEquals(listOf("new-srv"), manager.serverSegments)
	}

	@Test
	fun `getServerSegments resolves first`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList(), segments = listOf("srv")))
		assertEquals(listOf("srv"), manager.getServerSegments())
		coVerify(exactly = 1) { api.resolve(any()) }
	}

	// endregion

	// region server properties

	@Test
	fun `syncServerProperties mirrors the whole map unfiltered`() {
		env.masterId = UUID_A
		manager.syncServerProperties(mapOf("plan" to "premium", "geo_city" to "Barcelona"))
		assertEquals(mapOf("plan" to "premium", "geo_city" to "Barcelona"), manager.serverProperties)
		assertEquals(mapOf("plan" to "premium", "geo_city" to "Barcelona"), env.serverPropertiesStore.read(account, UUID_A))
	}

	@Test
	fun `syncServerProperties treats a missing properties field as an empty map`() {
		env.masterId = UUID_A
		manager.syncServerProperties(null)
		assertEquals(emptyMap<String, String>(), manager.serverProperties)
		assertEquals(emptyMap<String, String>(), env.serverPropertiesStore.read(account, UUID_A))
	}

	@Test
	fun `syncServerProperties no-ops without a master_id`() {
		manager.syncServerProperties(mapOf("a" to "b"))
		assertEquals(emptyMap<String, String>(), manager.serverProperties)
	}

	@Test
	fun `a link persists the properties from the response`() = runBlocking {
		env.consent = true
		resolveReturns(UNKNOWN_CDP_IDENTITY)
		coEvery { api.link(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList(), properties = mapOf("role" to "editor"))
		manager.linkIdentity("email", "u@x.com", true)
		assertEquals(mapOf("role" to "editor"), env.serverPropertiesStore.read(account, UUID_A))
	}

	@Test
	fun `server properties are dropped on a master_id change and kept when unchanged`() = runBlocking {
		env.consent = true
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList(), properties = mapOf("a" to "1")))
		manager.resolveIdentity()
		coEvery { api.update(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList())
		manager.updateProfile(listOf("k" to "v"))
		assertEquals(mapOf("a" to "1"), env.serverPropertiesStore.read(account, UUID_A))

		coEvery { api.link(any()) } returns CdpIdentityResponse(UUID_B, null, emptyList())
		manager.linkIdentity("email", "u@x.com", true)
		assertNull(env.serverPropertiesStore.read(account, UUID_A))
	}

	// endregion

	// region deleteIdentity

	@Test
	fun `deleteIdentity posts the identity under the master and refreshes the mirrors`() = runBlocking {
		env.consent = true
		env.warmVisitor()
		coEvery { api.delete(any()) } returns CdpDeleteResponse(
			CdpIdentityResponse(UUID_A, CdpRfv(2, 2, 2, 2), listOf(9), segments = listOf("left"), properties = mapOf("p" to "q")),
			deleted = 1
		)

		manager.deleteIdentity("email", "u@x.com")

		val params = slot<CdpDeleteParams>()
		coVerify { api.delete(capture(params)) }
		assertEquals(456L, params.captured.siteId)
		assertEquals(UUID_A, params.captured.masterId)
		assertEquals("email", params.captured.idType)
		assertEquals("u@x.com", params.captured.idValue)
		assertEquals(listOf("left"), manager.serverSegments)
		assertEquals(mapOf("p" to "q"), manager.serverProperties)
		assertEquals(2, env.cached?.rfv?.rfv)
	}

	@Test
	fun `deleteIdentity omits id_value when no value is given`() = runBlocking {
		env.consent = true
		env.warmVisitor()
		coEvery { api.delete(any()) } returns CdpDeleteResponse(CdpIdentityResponse(UUID_A, null, emptyList()), 3)

		manager.deleteIdentity("crm_id", null)

		val params = slot<CdpDeleteParams>()
		coVerify { api.delete(capture(params)) }
		assertNull(params.captured.idValue)
	}

	@Test
	fun `deleteIdentity skips without consent`() = runBlocking {
		env.consent = false
		env.masterId = UUID_A
		manager.deleteIdentity("email", "x")
		coVerify(exactly = 0) { api.delete(any()) }
	}

	@Test
	fun `deleteIdentity skips without a master_id`() = runBlocking {
		env.consent = true
		resolveReturns(UNKNOWN_CDP_IDENTITY)
		manager.deleteIdentity("email", "x")
		coVerify(exactly = 0) { api.delete(any()) }
	}

	@Test
	fun `deleteIdentity does not mark the identity fresh`() = runBlocking {
		env.consent = true
		env.warmVisitor()
		coEvery { api.delete(any()) } returns CdpDeleteResponse(CdpIdentityResponse(UUID_A, null, emptyList()), 1)
		manager.deleteIdentity("email", "x")
		assertFalse(manager.identityFresh)
	}

	@Test
	fun `deleteIdentity leaves state alone when the call fails`() = runBlocking {
		env.consent = true
		env.warmVisitor(segments = listOf("keep"))
		manager.resolveIdentity()
		coEvery { api.delete(any()) } returns null
		manager.deleteIdentity("email", "x")
		assertEquals(listOf("keep"), manager.serverSegments)
		assertEquals(1, env.cached?.rfv?.rfv)
	}

	// endregion

	// region clearIdentity

	@Test
	fun `clearIdentity wipes the master, the cache (as absent) and the in-memory mirrors`() = runBlocking {
		env.consent = true
		resolveReturns(CdpIdentityResponse(UUID_A, CdpRfv(1, 1, 1, 1), listOf(1), segments = listOf("srv"), properties = mapOf("a" to "b")))
		manager.resolveIdentity()

		manager.clearIdentity()

		assertNull(env.masterId)
		assertNull(env.cached)
		assertEquals(emptyList<String>(), manager.serverSegments)
		assertEquals(emptyMap<String, String>(), manager.serverProperties)
		assertNull(manager.getUserProfile().masterId)
	}

	@Test
	fun `clearIdentity clears the mid-scoped storage of the previous master and resets the pointer`() {
		env.masterId = UUID_A
		env.segmentsStore.write(account, UUID_A, listOf("mine"))
		env.segmentsStore.setActiveMid(account, UUID_A)
		env.serverSegmentsStore.write(account, UUID_A, listOf("srv"))
		env.serverPropertiesStore.write(account, UUID_A, mapOf("a" to "b"))
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("1", CdpConsentStatus.ACCEPTED, 1L))

		manager.clearIdentity()

		assertEquals(emptyList<String>(), env.segmentsStore.read(account, UUID_A))
		assertNull(env.serverSegmentsStore.read(account, UUID_A))
		assertNull(env.serverPropertiesStore.read(account, UUID_A))
		assertEquals(emptyMap<String, CdpRememberedConsentDecision>(), env.consentMemory.getRemembered(account))
		assertEquals(LOCAL_MID_SENTINEL, env.segmentsStore.getActiveMid(account))
		assertEquals(listOf(account to UUID_A), env.clearedBuckets)
	}

	@Test
	fun `clearIdentity falls back to the pointer then the sentinel when no master_id is known`() {
		env.segmentsStore.setActiveMid(account, UUID_B)
		manager.clearIdentity()
		assertEquals(listOf(account to UUID_B), env.clearedBuckets)

		env.clearedBuckets.clear()
		manager.clearIdentity()
		assertEquals(listOf(account to LOCAL_MID_SENTINEL), env.clearedBuckets)
	}

	@Test
	fun `clearIdentity drops the resolve memo so the next visitor mints a fresh master_id`() = runBlocking {
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		manager.resolveIdentity()

		manager.clearIdentity()
		resolveReturns(CdpIdentityResponse(UUID_B, null, emptyList()))
		manager.resolveIdentity()

		coVerify(exactly = 2) { api.resolve(any()) }
		assertEquals(UUID_B, env.masterId)
	}

	@Test
	fun `clearIdentity re-arms the one-shot onIdentityResolved callback`() = runBlocking {
		var fires = 0
		env.consent = true
		manager.onIdentityResolved { fires++ }
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		manager.resolveIdentity()
		assertEquals(1, fires)

		manager.clearIdentity()
		manager.resolveIdentity()
		assertEquals(2, fires)
	}

	@Test
	fun `clearIdentity never touches the network`() {
		env.masterId = UUID_A
		manager.clearIdentity()
		coVerify(exactly = 0) { api.resolve(any()) }
		coVerify(exactly = 0) { api.reset(any()) }
	}

	@Test
	fun `a resolve that started before clearIdentity does not resurrect the old master`() = runBlocking {
		env.consent = true
		var releaseResolve: (() -> Unit)? = null
		coEvery { api.resolve(any()) } coAnswers {
			kotlinx.coroutines.suspendCancellableCoroutine { cont ->
				releaseResolve = { cont.resume(CdpIdentityResponse(UUID_A, null, emptyList()), null) }
			}
		}
		val inFlight = GlobalScope.async { manager.resolveIdentity() }
		// let the coroutine reach the network call
		while (releaseResolve == null) delay(5)

		manager.clearIdentity()
		releaseResolve!!.invoke()
		inFlight.await()

		assertNull(env.masterId)
		assertNull(env.cached)
	}

	@Test
	fun `an update that started before clearIdentity does not resurrect the old master`() = runBlocking {
		env.consent = true
		env.masterId = UUID_A
		var releaseUpdate: (() -> Unit)? = null
		coEvery { api.update(any()) } coAnswers {
			kotlinx.coroutines.suspendCancellableCoroutine { cont ->
				releaseUpdate = { cont.resume(CdpIdentityResponse(UUID_A, null, emptyList()), null) }
			}
		}
		val profile = GlobalScope.async { manager.updateProfile(listOf("plan" to "gold")) }
		while (releaseUpdate == null) delay(5)

		manager.clearIdentity()
		releaseUpdate!!.invoke()
		profile.await()

		assertNull(env.masterId)
		assertNull(env.cached)
		assertTrue(env.masterIdChanges.isEmpty())
	}

	@Test
	fun `a segment change that started before clearIdentity does not resurrect the old master`() = runBlocking {
		env.consent = true
		env.masterId = UUID_A
		var releaseUpdate: (() -> Unit)? = null
		coEvery { api.update(any()) } coAnswers {
			kotlinx.coroutines.suspendCancellableCoroutine { cont ->
				releaseUpdate = { cont.resume(CdpIdentityResponse(UUID_A, null, emptyList()), null) }
			}
		}
		val change = GlobalScope.async { manager.addSegment("sports") }
		while (releaseUpdate == null) delay(5)

		manager.clearIdentity()
		releaseUpdate!!.invoke()
		change.await()

		assertNull(env.masterId)
		assertEquals(LOCAL_MID_SENTINEL, env.segmentsStore.getActiveMid(account))
	}

	@Test
	fun `resetRemoteIdentity posts the site id and is inert when disabled`() = runBlocking {
		coEvery { api.reset(456L) } returns com.marfeel.compass.cdp.model.CdpResetResponse(true, 456L, emptyList())
		assertTrue(manager.resetRemoteIdentity()!!.reset)

		env.enabled = false
		assertNull(manager.resetRemoteIdentity())
		coVerify(exactly = 1) { api.reset(any()) }
	}

	// endregion

	// region profile / segments (device-owned)

	@Test
	fun `updateProfile stringifies and no-ops without a master_id`() = runBlocking {
		manager.updateProfile(listOf("k" to "v"))
		coVerify(exactly = 0) { api.update(any()) }

		env.masterId = UUID_A
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY
		manager.updateProfile(listOf("timezone" to "Europe/Madrid"))
		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals("Europe/Madrid", params.captured.properties?.get("timezone"))
	}

	@Test
	fun `segments are written locally first then synced`() = runBlocking {
		env.consent = true
		env.masterId = UUID_A
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY

		manager.addSegment("sports")

		assertEquals(listOf("sports"), env.segmentsStore.read(account, UUID_A))
		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals(listOf("sports"), params.captured.segmentsAdd)
	}

	@Test
	fun `pre-consent segments use the local bucket and are not synced`() = runBlocking {
		manager.addSegment("tech")
		assertEquals(listOf("tech"), env.segmentsStore.read(account, LOCAL_MID_SENTINEL))
		coVerify(exactly = 0) { api.update(any()) }
	}

	@Test
	fun `replaceSegments diffs against the previous local snapshot`() = runBlocking {
		env.consent = true
		env.masterId = UUID_A
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY
		env.segmentsStore.write(account, UUID_A, listOf("a", "b"))

		manager.replaceSegments(listOf("b", "c", "c"))

		assertEquals(listOf("b", "c"), env.segmentsStore.read(account, UUID_A))
		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals(listOf("c"), params.captured.segmentsAdd)
		assertEquals(listOf("a"), params.captured.segmentsRemove)
	}

	@Test
	fun `reconcile pushes segments_add only`() = runBlocking {
		env.consent = true
		env.masterId = UUID_A
		coEvery { api.update(any()) } returns UNKNOWN_CDP_IDENTITY
		env.segmentsStore.write(account, UUID_A, listOf("x", "y"))

		manager.reconcileSegments()

		val params = slot<CdpProfileUpdateParams>()
		coVerify { api.update(capture(params)) }
		assertEquals(listOf("x", "y"), params.captured.segmentsAdd)
		assertNull(params.captured.segmentsRemove)
	}

	@Test
	fun `carry-over unions the local bucket into the new master_id and clears the old`() {
		env.segmentsStore.write(account, LOCAL_MID_SENTINEL, listOf("pre1", "pre2"))

		manager.transferCdpSegments(account, oldId = null, newId = UUID_A)

		assertEquals(listOf("pre1", "pre2"), env.segmentsStore.read(account, UUID_A))
		assertEquals(emptyList<String>(), env.segmentsStore.read(account, LOCAL_MID_SENTINEL))
		assertEquals(UUID_A, env.segmentsStore.getActiveMid(account))
	}

	@Test
	fun `one-shot fires once when identity becomes ready`() = runBlocking {
		var fires = 0
		manager.onIdentityResolved { fires++ }
		assertEquals(0, fires)

		env.consent = true
		resolveReturns(CdpIdentityResponse(UUID_A, null, emptyList()))
		manager.resolveIdentity()
		assertEquals(1, fires)

		manager.resolveIdentity()
		assertEquals(1, fires)
	}

	@Test
	fun `getUserProfile returns empty triple when disabled`() {
		env.enabled = false
		val data = manager.getUserProfile(serialized = true)
		assertNull(data.masterId)
		assertNull(data.rfv)
		assertTrue(data.cohorts.isEmpty())
		assertFalse(data.identityFresh)
	}

	@Test
	fun `getUserProfile serializes rfv and cohorts`() {
		env.masterId = UUID_A
		env.cached = CdpCachedIdentity(CdpRfv(42, 3, 5, 7), listOf(101, 204))
		env.cachedSession = env.sessionId
		val data = manager.getUserProfile(serialized = true)
		assertEquals(UUID_A, data.masterId)
		assertTrue(data.rfvSerialized.contains("\"rfv\":42"))
		assertEquals("[101,204]", data.cohortsSerialized)
	}

	// endregion
}
