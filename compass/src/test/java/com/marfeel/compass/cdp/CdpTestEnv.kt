package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.cdp.store.CdpConsentMemoryStore
import com.marfeel.compass.cdp.store.CdpSegmentsStore
import com.marfeel.compass.cdp.store.CdpServerPropertiesStore
import com.marfeel.compass.cdp.store.CdpServerSegmentsStore
import com.marfeel.compass.storage.MockSharedPreference
import io.mockk.mockk

internal const val UUID_A = "550e8400-e29b-41d4-a716-446655440000"
internal const val UUID_B = "550e8400-e29b-41d4-a716-446655440001"

/**
 * In-memory stand-in for everything [CdpManager] is wired to in `di.kt`: the
 * SharedPreferences-backed stores are real (over a mock prefs), the tracker-side
 * hooks are plain fields the test can read and poke.
 */
internal class CdpTestEnv(
	val api: CdpApiClient = mockk(),
	val prefs: MockSharedPreference = MockSharedPreference(),
	val consentMemory: CdpConsentMemoryStore = CdpConsentMemoryStore(prefs)
) {
	val segmentsStore = CdpSegmentsStore(prefs)
	val serverSegmentsStore = CdpServerSegmentsStore(prefs)
	val serverPropertiesStore = CdpServerPropertiesStore(prefs)

	var enabled = true
	var consent: Boolean? = null
	var account: String? = "456"
	var masterId: String? = null
	var userId = "cookie-1"
	var sessionId = "session-1"
	var cached: CdpCachedIdentity? = null
	var cachedSession: String? = null
	var ownedSegments: List<String> = emptyList()
	var timezone: String? = "Europe/Madrid"
	var now = 1_000L

	val masterIdChanges = mutableListOf<Pair<String?, String>>()
	val clearedBuckets = mutableListOf<Pair<String?, String>>()

	val manager: CdpManager = CdpManager(
		isEnabled = { enabled },
		api = api,
		accountId = { account },
		getMasterId = { masterId },
		writeMasterId = { new -> val old = masterId; masterId = new; old },
		clearMasterId = { masterId = null },
		getUserId = { userId },
		getCachedIdentity = { s -> if (s == cachedSession) cached else null },
		setCachedIdentity = { rfv: CdpRfv?, cohorts: List<Int>, s: String ->
			cached = CdpCachedIdentity(rfv, cohorts); cachedSession = s
		},
		clearCachedIdentity = { cached = null; cachedSession = null },
		getConsent = { consent },
		getSessionId = { sessionId },
		segmentsStore = segmentsStore,
		serverSegmentsStore = serverSegmentsStore,
		serverPropertiesStore = serverPropertiesStore,
		consentMemory = consentMemory,
		getOwnedSegments = { ownedSegments },
		timezone = { timezone },
		clock = { now }
	).apply {
		onMasterIdChanged = { old, new -> masterIdChanges.add(old to new) }
		onIdentityCleared = { acc, mid -> clearedBuckets.add(acc to mid) }
	}

	/** A warm returning visitor: master + session-tagged cache + both mirrors present. */
	fun warmVisitor(segments: List<String> = emptyList(), properties: Map<String, String> = emptyMap()) {
		masterId = UUID_A
		cached = CdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(1))
		cachedSession = sessionId
		serverSegmentsStore.write(account, UUID_A, segments)
		serverPropertiesStore.write(account, UUID_A, properties)
	}
}
