package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpConsent
import com.marfeel.compass.cdp.model.CdpConsentDefinition
import com.marfeel.compass.cdp.model.CdpConsentQuery
import com.marfeel.compass.cdp.model.CdpConsentRecordResponse
import com.marfeel.compass.cdp.model.CdpConsentRef
import com.marfeel.compass.cdp.model.CdpData
import com.marfeel.compass.cdp.model.CdpResetResponse
import com.marfeel.compass.cdp.model.MeterNotFoundError
import com.marfeel.compass.cdp.model.MeterState
import com.marfeel.compass.di.CompassComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Public entry point for the Customer Data Platform (CDP) subsystem: stable visitor
 * identity (master_id), read-only RFV/cohorts attached to beacons, host-pushed
 * segments + properties, server-authoritative meters and publisher consents.
 *
 * This interface is the Android counterpart of the web `compass.cdp` namespace and is
 * treated as **add-only**: names are never renamed or removed once shipped (a test pins
 * them). Superseded names stay as deprecated delegates.
 *
 * Everything here is inert — no network, `null` / `false` / empty answers — unless the
 * SDK was initialized with `enableCdp = true`. Identity calls are additionally gated
 * on personalization consent (`CompassTracking.setUserConsent`); the publisher-consent
 * calls ([trackConsent], [getConsent], [hasConsent]) deliberately are **not**.
 */
interface Cdp {
	// region identity

	/**
	 * Link an external identifier to the current visitor and adopt the master the CDP
	 * resolves it to. Suspends until the link round-trip completes (or is skipped for
	 * lack of consent). See [CdpIdentityTypes] for the well-known [type]s.
	 *
	 * @param isDeterministic forces a device-bound type to make the user registered.
	 */
	suspend fun setIdentity(type: String, value: String, isDeterministic: Boolean = false)

	/**
	 * Unlink an identity from the current master. With a null [value], every identity of
	 * [type] the master owns is unlinked. Throws [IllegalArgumentException] on an empty
	 * [type] — it names nothing to delete. No-op without a master or consent.
	 */
	suspend fun deleteIdentity(type: String, value: String? = null)

	/** The current CDP master_id, or null before the first resolve / without consent. */
	fun getMasterId(): String?

	/**
	 * The CDP's contribution to a beacon: master_id, read-only rfv/cohorts and whether
	 * this process resolved the identity itself. [serialized] fills the JSON-string
	 * forms of rfv/cohorts.
	 */
	fun getUserProfile(serialized: Boolean = false): CdpData

	@Deprecated("Use setIdentity(type, value, isDeterministic)", ReplaceWith("setIdentity(type, value, isDeterministic)"))
	fun cdpDoIdentityLink(type: String, value: String, isDeterministic: Boolean = false)

	@Deprecated("Use getUserProfile(serialized)", ReplaceWith("getUserProfile(serialized)"))
	fun getCdpData(serialized: Boolean = false): CdpData

	@Deprecated("Use getMasterId()", ReplaceWith("getMasterId()"))
	fun getCdpMasterId(): String?

	// endregion

	// region identity types & hashing

	/** The well-known identity types; see [CdpIdentityTypes]. */
	val identityTypes: CdpIdentityTypes
		get() = CdpIdentityTypes

	/** `trim` + lower-case, the server's rule. */
	fun normalizeEmail(email: String): String

	/** `trim` only — never case-folded; `+34600111222` and `600111222` stay two users. */
	fun normalizePhone(phone: String): String

	/** SHA-256 hex of [normalizeEmail]; send under [CdpIdentityTypes.EMAIL_SHA256]. */
	fun hashEmail(email: String): String

	/** SHA-256 hex of [normalizePhone]; send under [CdpIdentityTypes.PHONE_SHA256]. */
	fun hashPhone(phone: String): String

	// endregion

	// region publisher consents

	/**
	 * Record that the visitor accepted or rejected a publisher consent (`POST
	 * /cdp/consents/record/`). Not gated on personalization consent. Recorded under the
	 * current master when one exists; an anonymous decision is remembered locally and
	 * replayed once a master exists. Null on failure or when CDP is disabled.
	 */
	suspend fun trackConsent(decision: CdpConsent): CdpConsentRecordResponse?

	/** Read a consent's definition from the catalog. Null when unknown, on failure, or when CDP is disabled. */
	suspend fun getConsent(ref: CdpConsentRef): CdpConsentDefinition?

	/**
	 * Whether the visitor has **accepted** the consent (at exactly [CdpConsentQuery.versionId]
	 * when given). False — never null — on failure and when CDP is disabled. Answered
	 * from local memory when the device has neither a master nor an email to ask with.
	 */
	suspend fun hasConsent(query: CdpConsentQuery): Boolean

	// endregion

	// region segments & properties

	fun addCdpSegment(segment: String)

	fun removeCdpSegment(segment: String)

	fun setCdpSegments(segments: List<String>)

	fun clearCdpSegments()

	/** Synchronous read of the local CDP segment mirror for the current identity. */
	fun getCdpSegments(): List<String>

	/** Server Segments (asserted by the CDP, not by this device) known right now, without resolving. */
	fun listServerSegments(): List<String>

	/** Server Segments after an identity resolve. */
	suspend fun getServerSegments(): List<String>

	/** Server Properties (computed by the CDP) known right now, without resolving. */
	fun listServerProperties(): Map<String, String>

	/** Server Properties after an identity resolve. */
	suspend fun getServerProperties(): Map<String, String>

	// endregion

	// region meters

	/** Refresh and return the meters (stale-while-revalidate; fail-open). */
	suspend fun getMeterSnapshot(): List<MeterState>

	/** Synchronous read of the in-memory meter mirror. */
	fun getMeter(name: String): MeterState?

	fun listMeters(): List<MeterState>

	/** Increment a meter. Throws [MeterNotFoundError] if the meter isn't configured. */
	suspend fun incrementMeter(name: String): MeterState?

	// endregion

	companion object {
		fun getInstance(): Cdp = CdpTracker
	}
}

internal object CdpTracker : Cdp {
	private val cdpManager: CdpManager by lazy { CompassComponent.cdpManager }
	private val meteredCounter: MeteredCounter by lazy { CompassComponent.meteredCounter }
	private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private const val USER_VARS_FLUSH_DEBOUNCE_MS = 50L

	@Volatile
	private var userVarsFlush: Job? = null

	// region identity

	override suspend fun setIdentity(type: String, value: String, isDeterministic: Boolean) {
		cdpManager.linkIdentity(type, value, isDeterministic)
	}

	override suspend fun deleteIdentity(type: String, value: String?) {
		require(type.isNotEmpty()) { "Cdp.deleteIdentity: type is required" }
		cdpManager.deleteIdentity(type, value)
	}

	override fun getMasterId(): String? = cdpManager.currentMasterId()

	override fun getUserProfile(serialized: Boolean): CdpData = cdpManager.getUserProfile(serialized)

	@Deprecated("Use setIdentity(type, value, isDeterministic)", ReplaceWith("setIdentity(type, value, isDeterministic)"))
	override fun cdpDoIdentityLink(type: String, value: String, isDeterministic: Boolean) {
		coroutineScope.launch { cdpManager.linkIdentity(type, value, isDeterministic) }
	}

	@Deprecated("Use getUserProfile(serialized)", ReplaceWith("getUserProfile(serialized)"))
	override fun getCdpData(serialized: Boolean): CdpData = getUserProfile(serialized)

	@Deprecated("Use getMasterId()", ReplaceWith("getMasterId()"))
	override fun getCdpMasterId(): String? = getMasterId()

	// endregion

	// region hashing

	override fun normalizeEmail(email: String): String = CdpHash.normalizeEmail(email)

	override fun normalizePhone(phone: String): String = CdpHash.normalizePhone(phone)

	override fun hashEmail(email: String): String = CdpHash.hashEmail(email)

	override fun hashPhone(phone: String): String = CdpHash.hashPhone(phone)

	// endregion

	// region publisher consents

	/** Best-effort resolve first, so a consented visitor records under a master. */
	override suspend fun trackConsent(decision: CdpConsent): CdpConsentRecordResponse? {
		cdpManager.resolveIdentity()
		return cdpManager.trackCdpConsent(decision)
	}

	/** A pure catalog read — no identity resolve. */
	override suspend fun getConsent(ref: CdpConsentRef): CdpConsentDefinition? = cdpManager.getCdpConsent(ref)

	/** Resolves first, so a returning visitor with no cached master gets a subject. */
	override suspend fun hasConsent(query: CdpConsentQuery): Boolean {
		cdpManager.resolveIdentity()
		return cdpManager.hasCdpConsent(query)
	}

	// endregion

	// region segments & properties

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

	override fun listServerSegments(): List<String> = cdpManager.serverSegments

	override suspend fun getServerSegments(): List<String> = cdpManager.getServerSegments()

	override fun listServerProperties(): Map<String, String> = cdpManager.serverProperties

	override suspend fun getServerProperties(): Map<String, String> = cdpManager.getServerProperties()

	// endregion

	// region meters

	override suspend fun getMeterSnapshot(): List<MeterState> = meteredCounter.getMeterSnapshot()

	override fun getMeter(name: String): MeterState? = meteredCounter.get(name)

	override fun listMeters(): List<MeterState> = meteredCounter.list()

	override suspend fun incrementMeter(name: String): MeterState? = meteredCounter.increment(name)

	// endregion

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
		coroutineScope.launch { cdpManager.linkIdentity(CdpIdentityTypes.REGISTERED_USER_ID, userId, isDeterministic = true) }
	}

	/**
	 * Push the device-owned user vars to the CDP profile, debounced so a burst of
	 * `setUserVar` calls lands as one `/update/`. Only owned vars travel — Server
	 * Properties are never echoed back.
	 */
	internal fun flushUserVars(ownedUserVars: () -> Map<String, String>) {
		userVarsFlush?.cancel()
		userVarsFlush = coroutineScope.launch {
			delay(USER_VARS_FLUSH_DEBOUNCE_MS)
			cdpManager.updateProfile(ownedUserVars().toList())
		}
	}

	/**
	 * The synchronous local CDP wipe behind `resetUser()`: master_id, cached rfv/cohorts
	 * (as absent), resolve memo, freshness, Server Segment / Property mirrors, meters,
	 * per-master storage, anonymous consent memory and the active-mid pointer.
	 */
	internal fun clearIdentity() {
		cdpManager.clearIdentity()
	}

	/** The best-effort remote tail of `resetUser()`; null on failure, inert when disabled. */
	internal suspend fun resetRemoteIdentity(): CdpResetResponse? = cdpManager.resetRemoteIdentity()

	/**
	 * Register the one-shot work that runs once identity is ready: reconcile local
	 * segments, push the current user vars + timezone, seed/cleanup meters, and replay
	 * the consent decisions recorded while anonymous.
	 */
	internal fun registerOnIdentityResolved(
		userVars: () -> Map<String, String>,
		timezone: String,
		legacySegments: () -> List<String>,
		writeLegacySegments: (List<String>) -> Unit
	) {
		cdpManager.onIdentityResolved {
			coroutineScope.launch {
				// Bridge legacy `useg` segments into the CDP store, then reconcile so the
				// unioned set is what gets pushed as segments_add (matches web ordering).
				val merged = cdpManager.mergeLegacySegments(legacySegments())
				writeLegacySegments(merged)
				cdpManager.reconcileSegments()
				val properties = userVars().toList() + ("timezone" to timezone)
				cdpManager.updateProfile(properties)
				meteredCounter.seed()
				meteredCounter.cleanupExpiredMeters(cdpManager.currentAccountId(), cdpManager.currentMasterId())
				cdpManager.replayConsentDecisions()
			}
		}
	}

	// endregion
}
