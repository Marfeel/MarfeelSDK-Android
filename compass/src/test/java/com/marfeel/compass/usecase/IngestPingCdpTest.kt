package com.marfeel.compass.usecase

import com.marfeel.compass.cdp.CdpTestEnv
import com.marfeel.compass.cdp.UUID_A
import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.ping.IngestPingEmitterState
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The beacon side of the CDP: `useg` / `uvar` come from the injected providers (the
 * tracker's merged, trimmed views) and `cdp_fresh` rides along only when this process
 * resolved the identity itself.
 */
internal class IngestPingCdpTest {
	private val storage = mockk<Storage>(relaxed = true)
	private val apiClient = mockk<ApiClient>(relaxed = true)
	private val sessionStorage = SessionStorage(storage)
	private lateinit var env: CdpTestEnv
	private val page = Page("https://x.com/a")

	@Before
	fun setUp() {
		env = CdpTestEnv()
		env.consent = true
		every { storage.readSession() } returns Session("s1", 1L)
		every { storage.readOriginalUserId() } returns "u1"
		every { storage.readUserConsent() } returns true
		every { storage.readFirstSessionTimeStamp() } returns 1L
		sessionStorage.updateAccountId("456")
		sessionStorage.setPageTechnology(4)
		sessionStorage.setCdpEnabled(true)
		sessionStorage.updatePage(page)

		mockkObject(CompassComponent)
		every { CompassComponent.cdpManager } returns env.manager
	}

	@After
	fun tearDown() {
		unmockkObject(CompassComponent)
	}

	private fun ping(
		segments: () -> List<String> = { emptyList() },
		vars: () -> Map<String, String> = { emptyMap() }
	) = IngestPing(apiClient, sessionStorage, storage, userSegmentsProvider = segments, userVarsProvider = vars)

	private val state = IngestPingEmitterState(page.url, page.pageId, "s1", 0, 0L, 0L)

	@Test
	fun `useg and uvar come from the merged providers`() {
		val data = ping(
			segments = { listOf("server-seg", "device-seg") },
			vars = { mapOf("plan" to "premium", "geo" to "BCN") }
		).getData(state)!!

		assertEquals(listOf("server-seg", "device-seg"), data.userSegments)
		assertEquals(mapOf("plan" to "premium", "geo" to "BCN"), data.userVars)
	}

	@Test
	fun `cdp_fresh is attached only after this process resolved the identity`() = runBlocking {
		// warm visitor: nothing round-tripped
		env.warmVisitor()
		env.manager.resolveIdentity()
		var data = ping().getData(state)!!
		assertEquals(UUID_A, data.cdpMasterId)
		assertNull(data.cdpFresh)

		// a real resolve in this process
		env.cached = null
		env.cachedSession = null
		env.sessionId = "session-2"
		coEvery { env.api.resolve(any()) } returns CdpIdentityResponse(UUID_A, CdpRfv(1, 1, 1, 1), listOf(1))
		env.manager.resolveIdentity()
		data = ping().getData(state)!!
		assertEquals("1", data.cdpFresh)
	}

	@Test
	fun `cdp fields are absent after a reset`() = runBlocking {
		coEvery { env.api.resolve(any()) } returns CdpIdentityResponse(UUID_A, CdpRfv(1, 1, 1, 1), listOf(1))
		env.manager.resolveIdentity()
		env.manager.clearIdentity()

		val data = ping().getData(state)!!

		assertNull(data.cdpMasterId)
		assertNull(data.cdpRfv)
		assertNull(data.cdpCohorts)
		assertNull(data.cdpFresh)
	}

	@Test
	fun `cdp fields are absent without personalization consent even with a cached master`() {
		env.masterId = UUID_A
		env.cached = CdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(1))
		env.cachedSession = env.sessionId
		env.consent = false

		val data = ping().getData(state)!!

		assertNull(data.cdpMasterId)
	}
}
