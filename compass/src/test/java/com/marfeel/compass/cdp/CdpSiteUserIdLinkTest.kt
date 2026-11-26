package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpLinkParams
import io.mockk.coEvery
import io.mockk.coVerify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * The stored login is linked again when the CDP starts, at each new session and on a
 * consent change — `setSiteUserId` alone left readers who logged in before the CDP was
 * enabled on an anonymous master. [CdpManager.relinkSiteUserId] is what those triggers
 * call; it links at most once per (session, generation, id).
 */
internal class CdpSiteUserIdLinkTest {
	private lateinit var env: CdpTestEnv
	private val manager get() = env.manager
	private val api get() = env.api
	private val links = mutableListOf<CdpLinkParams>()

	@Before
	fun setUp() {
		env = CdpTestEnv()
		env.consent = true
		env.warmVisitor()
		links.clear()
		coEvery { api.resolve(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList())
		coEvery { api.link(capture(links)) } returns CdpIdentityResponse(UUID_A, null, emptyList())
	}

	@Test
	fun `relink links the stored id as a deterministic registered_user_id on the current master`() = runBlocking {
		manager.relinkSiteUserId("user-1")

		assertEquals(
			listOf(CdpLinkParams(456, CdpIdentityTypes.REGISTERED_USER_ID, "user-1", true, UUID_A)),
			links
		)
	}

	@Test
	fun `relink runs once per session`() = runBlocking {
		manager.relinkSiteUserId("user-1")
		manager.relinkSiteUserId("user-1")

		assertEquals(1, links.size)
	}

	@Test
	fun `relink runs again in a new session`() = runBlocking {
		manager.relinkSiteUserId("user-1")

		env.sessionId = "session-2"
		manager.relinkSiteUserId("user-1")

		assertEquals(2, links.size)
	}

	@Test
	fun `relink runs again when the stored id changed`() = runBlocking {
		manager.relinkSiteUserId("user-1")
		manager.relinkSiteUserId("user-2")

		assertEquals(listOf("user-1", "user-2"), links.map { it.idValue })
	}

	@Test
	fun `relink without a stored id does nothing`() = runBlocking {
		manager.relinkSiteUserId(null)
		manager.relinkSiteUserId("")

		coVerify(exactly = 0) { api.link(any()) }
		coVerify(exactly = 0) { api.resolve(any()) }
	}

	@Test
	fun `relink without consent is not recorded, so the consent change that follows links`() = runBlocking {
		env.consent = false
		manager.relinkSiteUserId("user-1")
		assertEquals(0, links.size)

		env.consent = true
		manager.relinkSiteUserId("user-1")

		assertEquals(1, links.size)
	}

	@Test
	fun `relink is skipped while the CDP is disabled and runs once it is enabled`() = runBlocking {
		env.enabled = false
		manager.relinkSiteUserId("user-1")
		assertEquals(0, links.size)

		env.enabled = true
		manager.relinkSiteUserId("user-1")

		assertEquals(1, links.size)
	}

	@Test
	fun `relink runs again after a reset, on the new visitor's master`() = runBlocking {
		manager.relinkSiteUserId("user-1")

		manager.clearIdentity()
		coEvery { api.resolve(any()) } returns CdpIdentityResponse(UUID_B, null, emptyList())
		manager.relinkSiteUserId("user-1")

		assertEquals(listOf<String?>(UUID_A, UUID_B), links.map { it.masterId })
	}

	@Test
	fun `setSiteUserId always links, and the relink that follows in the same session is skipped`() = runBlocking {
		manager.linkSiteUserId("user-1")
		manager.linkSiteUserId("user-1")
		manager.relinkSiteUserId("user-1")

		assertEquals(2, links.size)
	}

	@Test
	fun `setSiteUserId without consent is linked by the relink once consent arrives`() = runBlocking {
		env.consent = false
		manager.linkSiteUserId("user-1")
		assertEquals(0, links.size)

		env.consent = true
		manager.relinkSiteUserId("user-1")

		assertEquals(listOf("user-1"), links.map { it.idValue })
	}

	@Test
	fun `setSiteUserId with an empty id is not linked`() = runBlocking {
		manager.linkSiteUserId("")

		coVerify(exactly = 0) { api.link(any()) }
	}
}
