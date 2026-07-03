package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.MeterNotFoundError
import com.marfeel.compass.cdp.model.MeterState
import com.marfeel.compass.cdp.store.CdpMetersStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stale-while-revalidate cache over the server-authoritative meters. Sync `get`/`list`
 * read the in-memory mirror (seeded from persistence so they have a last-known value
 * before any network);
 */
internal class MeteredCounter(
	private val cdpManager: CdpManager,
	private val metersStore: CdpMetersStore,
	private val api: CdpApiClient,
	private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
	private val mutex = Mutex()

	@Volatile
	private var meters: List<MeterState> = emptyList()

	@Volatile
	private var fresh = false
	private var inflight: Deferred<List<MeterState>?>? = null
	private var seeded: Deferred<Unit>? = null

	private fun ready(): Boolean = cdpManager.hasConsent() && cdpManager.currentMasterId() != null

	/** Fill the in-memory mirror from persistence once, so sync reads have a value. */
	suspend fun seed() {
		if (!ready()) return
		val deferred = mutex.withLock {
			seeded ?: scope.async { runSeed() }.also { seeded = it }
		}
		deferred.await()
	}

	private fun runSeed() {
		if (meters.isNotEmpty()) return
		val stored = metersStore.read(cdpManager.currentAccountId(), cdpManager.currentMasterId())
		if (meters.isEmpty() && stored.isNotEmpty()) {
			meters = stored
		}
	}

	suspend fun getMeterSnapshot(): List<MeterState> {
		if (!ready()) {
			return meters
		}
		seed()
		if (fresh) {
			return meters
		}

		val deferred = mutex.withLock {
			inflight ?: scope.async { fetchMeters() }.also { inflight = it }
		}
		val fetched = try {
			deferred.await()
		} finally {
			mutex.withLock { if (inflight === deferred) inflight = null }
		}

		if (fetched != null) {
			meters = fetched
			fresh = true
			persist(fetched)
		}
		return meters
	}

	private suspend fun fetchMeters(): List<MeterState>? {
		val account = cdpManager.currentAccountId() ?: return null
		val masterId = cdpManager.currentMasterId() ?: return null
		return api.fetchMeters(account, masterId)
	}

	/** mark the mirror stale so the next snapshot re-fetches. */
	fun invalidate() {
		fresh = false
	}

	fun get(name: String): MeterState? = meters.firstOrNull { it.name == name }

	fun list(): List<MeterState> = meters

	suspend fun increment(name: String): MeterState? {
		if (!ready()) return null
		val account = cdpManager.currentAccountId() ?: return null
		val masterId = cdpManager.currentMasterId() ?: return null

		val result = api.incrementMeter(name, account, masterId)
		if (result.status == 404) {
			throw MeterNotFoundError(name)
		}

		val state = result.state ?: return get(name)
		val updated = if (meters.any { it.name == name }) {
			meters.map { if (it.name == name) state else it }
		} else {
			meters + state
		}
		meters = updated
		persist(updated)
		return state
	}

	fun cleanupExpiredMeters(account: String?, activeMid: String?) {
		metersStore.cleanupExpired(account, activeMid)
	}

	fun reset() {
		meters = emptyList()
		fresh = false
		seeded = null
		inflight = null
	}

	private fun persist(value: List<MeterState>) {
		metersStore.write(cdpManager.currentAccountId(), cdpManager.currentMasterId(), value)
	}
}
