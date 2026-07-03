package com.marfeel.compass.cdp

import android.content.Context
import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.cdp.store.CdpSegmentsStore
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
 * The native cache-invalidation rules from plan §13: session rotation refreshes the
 * in-memory identity state and the session-tagged rfv/cohorts cache, a master_id change
 * resets the meter mirror, and `master_id` itself is the only intentionally-permanent
 * cache (survives session rotation).
 */
internal class CdpLifecycleTest {
	private lateinit var api: CdpApiClient
	private lateinit var manager: CdpManager

	private var account: String? = "456"
	private var masterId: String? = null
	private var sessionId = "session-1"
	private var cached: CdpCachedIdentity? = null
	private var cachedSession: String? = null
	private val masterIdChanges = mutableListOf<Pair<String?, String>>()

	private val uuidA = "550e8400-e29b-41d4-a716-446655440000"
	private val uuidB = "550e8400-e29b-41d4-a716-446655440001"

	companion object {
		@JvmStatic
		@BeforeClass
		fun beforeClass() {
			Security.addProvider(keyStoreProvider)
		}
	}

	@Before
	fun setUp() {
		api = mockk()
		masterId = null
		cached = null
		cachedSession = null
		sessionId = "session-1"
		masterIdChanges.clear()

		manager = CdpManager(
			isEnabled = { true },
			api = api,
			accountId = { account },
			getMasterId = { masterId },
			writeMasterId = { new -> val old = masterId; masterId = new; old },
			getUserId = { "cookie-1" },
			getCachedIdentity = { s -> if (s == cachedSession) cached else null },
			setCachedIdentity = { rfv, cohorts, s ->
				cached = CdpCachedIdentity(rfv, cohorts); cachedSession = s
			},
			getConsent = { true },
			getSessionId = { sessionId },
			segmentsStore = CdpSegmentsStore(MockSharedPreference())
		).apply {
			onMasterIdChanged = { old, new -> masterIdChanges.add(old to new) }
		}
	}

	@Test
	fun `master_id change fires the reset hook only when it actually changes`() = runBlocking {
		coEvery { api.resolve(any()) } returns CdpIdentityResponse(uuidA, null, emptyList())
		manager.resolveIdentity()
		assertEquals(listOf<Pair<String?, String>>(null to uuidA), masterIdChanges)

		// Linking returns a merged, different id → reset hook fires again.
		coEvery { api.link(any()) } returns CdpIdentityResponse(uuidB, null, emptyList())
		manager.linkIdentity("registered_user_id", "u", isDeterministic = true)
		assertEquals(uuidA to uuidB, masterIdChanges.last())
	}

	@Test
	fun `re-resolving the same id in a new session does not fire the reset hook`() = runBlocking {
		coEvery { api.resolve(any()) } returns CdpIdentityResponse(uuidA, null, emptyList())
		manager.resolveIdentity()
		masterIdChanges.clear()

		sessionId = "session-2"
		manager.resolveIdentity()
		// master_id stayed uuidA → no change event.
		assertEquals(emptyList<Pair<String?, String>>(), masterIdChanges)
	}

	@Test
	fun `master_id survives session rotation while rfv-cohorts cache does not`() {
		val context = mockk<Context>()
		every { context.getSharedPreferences(any(), any()) } returns MockSharedPreference()
		val storage = Storage(context)

		storage.writeCdpMasterId(uuidA)
		storage.writeCdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(7), "session-1")

		// New session: identity (master_id) persists, but the session-tagged cache reads
		// as absent, forcing a fresh resolve.
		assertEquals(uuidA, storage.readCdpMasterId())
		assertNull(storage.readCdpCachedIdentity("session-2"))
	}
}
