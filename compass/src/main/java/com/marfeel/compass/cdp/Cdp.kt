package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpData
import com.marfeel.compass.cdp.model.MeterNotFoundError
import com.marfeel.compass.cdp.model.MeterState
import com.marfeel.compass.di.CompassComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Public entry point for the Customer Data Platform (CDP) subsystem: stable visitor
 * identity (master_id), read-only RFV/cohorts attached to beacons, host-pushed
 * segments + properties, and server-authoritative meters.
 */
interface Cdp {
	/** Link an external identifier (e.g. a login id) to the current visitor. */
	fun cdpDoIdentityLink(type: String, value: String, isDeterministic: Boolean = false)

	/** The CDP's contribution to a beacon: master_id + read-only rfv/cohorts. */
	fun getCdpData(serialized: Boolean = false): CdpData

	fun getCdpMasterId(): String?

	fun addCdpSegment(segment: String)

	fun removeCdpSegment(segment: String)

	fun setCdpSegments(segments: List<String>)

	fun clearCdpSegments()

	/** Synchronous read of the local CDP segment mirror for the current identity. */
	fun getCdpSegments(): List<String>

	/** Refresh and return the meters (stale-while-revalidate; fail-open). */
	suspend fun getMeterSnapshot(): List<MeterState>

	/** Synchronous read of the in-memory meter mirror. */
	fun getMeter(name: String): MeterState?

	fun listMeters(): List<MeterState>

	/** Increment a meter. Throws [MeterNotFoundError] if the meter isn't configured. */
	suspend fun incrementMeter(name: String): MeterState?

	companion object {
		fun getInstance(): Cdp = CdpTracker
	}
}

internal object CdpTracker : Cdp {
	private val cdpManager: CdpManager by lazy { CompassComponent.cdpManager }
	private val meteredCounter: MeteredCounter by lazy { CompassComponent.meteredCounter }
	private val coroutineScope = CoroutineScope(Dispatchers.IO)

	override fun cdpDoIdentityLink(type: String, value: String, isDeterministic: Boolean) {
		coroutineScope.launch { cdpManager.linkIdentity(type, value, isDeterministic) }
	}

	override fun getCdpData(serialized: Boolean): CdpData = cdpManager.getData(serialized)

	override fun getCdpMasterId(): String? = cdpManager.currentMasterId()

	override fun addCdpSegment(segment: String) {
		coroutineScope.launch { cdpManager.addSegment(segment) }
	}

	override fun removeCdpSegment(segment: String) {
		coroutineScope.launch { cdpManager.removeSegment(segment) }
	}

	override fun setCdpSegments(segments: List<String>) {
		coroutineScope.launch { cdpManager.replaceSegments(segments) }
	}

	override fun clearCdpSegments() {
		coroutineScope.launch { cdpManager.clearSegments() }
	}

	override fun getCdpSegments(): List<String> = cdpManager.getCdpSegments()

	override suspend fun getMeterSnapshot(): List<MeterState> = meteredCounter.getMeterSnapshot()

	override fun getMeter(name: String): MeterState? = meteredCounter.get(name)

	override fun listMeters(): List<MeterState> = meteredCounter.list()

	override suspend fun incrementMeter(name: String): MeterState? = meteredCounter.increment(name)

	// region lifecycle hooks (called from CompassTracker)

	/** Re-resolve identity + invalidate meters on each page change. */
	internal fun onNewPage() {
		meteredCounter.invalidate()
		coroutineScope.launch { cdpManager.resolveIdentity() }
	}

	/**
	 * Revalidate the master_id at the start of every new SDK session (app open after the
	 * session window, fresh launch, etc.).
	 */
	internal fun onSessionStart() {
		meteredCounter.invalidate()
		coroutineScope.launch { cdpManager.resolveIdentity() }
	}

	internal fun onConsentChanged() {
		coroutineScope.launch { cdpManager.onConsentChanged() }
	}

	internal fun onSiteUserId(userId: String) {
		coroutineScope.launch { cdpManager.linkIdentity("registered_user_id", userId, isDeterministic = true) }
	}

	/**
	 * Register the one-shot work that runs once identity is ready: reconcile local
	 * segments, push the current user vars + timezone, and seed/cleanup meters.
	 */
	internal fun registerOnIdentityResolved(userVars: () -> Map<String, String>, timezone: String) {
		cdpManager.onIdentityResolved {
			coroutineScope.launch {
				cdpManager.reconcileSegments()
				val properties = userVars().toList() + ("timezone" to timezone)
				cdpManager.updateProfile(properties)
				meteredCounter.seed()
				meteredCounter.cleanupExpiredMeters(cdpManager.currentAccountId(), cdpManager.currentMasterId())
			}
		}
	}

	// endregion
}
