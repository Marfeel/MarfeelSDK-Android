package com.marfeel.compass.cdp

import android.content.Context
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.storage.MockSharedPreference
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.storage.keyStoreProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

/**
 * The native cache-invalidation rules: session rotation refreshes the in-memory
 * identity state and the session-tagged rfv/cohorts cache, a master_id change resets
 * the meter mirror, and `master_id` itself is the only intentionally-permanent cache
 * (survives session rotation, but not a user reset).
 */
internal class CdpLifecycleTest {
	private lateinit var env: CdpTestEnv
	private val manager get() = env.manager
	private val api get() = env.api

	companion object {
		@JvmStatic
		@BeforeClass
		fun beforeClass() {
			Security.addProvider(keyStoreProvider)
		}
	}

	@Before
	fun setUp() {
		env = CdpTestEnv()
		env.consent = true
	}

	@Test
	fun `master_id change fires the reset hook only when it actually changes`() = runBlocking {
		coEvery { api.resolve(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList())
		manager.resolveIdentity()
		assertEquals(listOf<Pair<String?, String>>(null to UUID_A), env.masterIdChanges)

		// Linking returns a merged, different id → reset hook fires again.
		coEvery { api.link(any()) } returns CdpIdentityResponse(UUID_B, null, emptyList())
		manager.linkIdentity("registered_user_id", "u", isDeterministic = true)
		assertEquals(UUID_A to UUID_B, env.masterIdChanges.last())
	}

	@Test
	fun `re-resolving the same id in a new session does not fire the reset hook`() = runBlocking {
		coEvery { api.resolve(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList())
		manager.resolveIdentity()
		env.masterIdChanges.clear()

		env.sessionId = "session-2"
		manager.resolveIdentity()
		// master_id stayed uuidA → no change event.
		assertEquals(emptyList<Pair<String?, String>>(), env.masterIdChanges)
	}

	@Test
	fun `master_id survives session rotation while rfv-cohorts cache does not`() {
		val context = mockk<Context>()
		every { context.getSharedPreferences(any(), any()) } returns MockSharedPreference()
		val storage = Storage(context)

		storage.writeCdpMasterId(UUID_A)
		storage.writeCdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(7), "session-1")

		// New session: identity (master_id) persists, but the session-tagged cache reads
		// as absent, forcing a fresh resolve.
		assertEquals(UUID_A, storage.readCdpMasterId())
		assertNull(storage.readCdpCachedIdentity("session-2"))
	}

	@Test
	fun `a user reset drops the master_id, the only otherwise-permanent cache`() {
		val context = mockk<Context>()
		every { context.getSharedPreferences(any(), any()) } returns MockSharedPreference()
		val storage = Storage(context)
		storage.writeCdpMasterId(UUID_A)
		storage.writeCdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(7), "session-1")

		storage.clearCdpMasterId()
		storage.clearCdpCachedIdentity()

		assertNull(storage.readCdpMasterId())
		assertNull(storage.readCdpCachedIdentity("session-1"))
	}
}
