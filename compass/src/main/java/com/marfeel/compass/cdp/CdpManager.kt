package com.marfeel.compass.cdp

import com.google.gson.Gson
import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpConsent
import com.marfeel.compass.cdp.model.CdpConsentCatalogItem
import com.marfeel.compass.cdp.model.CdpConsentCheck
import com.marfeel.compass.cdp.model.CdpConsentCheckParams
import com.marfeel.compass.cdp.model.CdpConsentCheckResponse
import com.marfeel.compass.cdp.model.CdpConsentDefinition
import com.marfeel.compass.cdp.model.CdpConsentQuery
import com.marfeel.compass.cdp.model.CdpConsentRecordParams
import com.marfeel.compass.cdp.model.CdpConsentRecordResponse
import com.marfeel.compass.cdp.model.CdpConsentRef
import com.marfeel.compass.cdp.model.CdpConsentShowPolicy
import com.marfeel.compass.cdp.model.CdpConsentStatus
import com.marfeel.compass.cdp.model.CdpConsentVersion
import com.marfeel.compass.cdp.model.CdpData
import com.marfeel.compass.cdp.model.CdpDeleteParams
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpRememberedConsentDecision
import com.marfeel.compass.cdp.model.CdpResetResponse
import com.marfeel.compass.cdp.model.CdpResolveParams
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.cdp.store.CdpConsentMemoryStore
import com.marfeel.compass.cdp.store.CdpSegmentsStore
import com.marfeel.compass.cdp.store.CdpServerPropertiesStore
import com.marfeel.compass.cdp.store.CdpServerSegmentsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/**
 * The CDP unit: identity resolution + linking, the Server Segments / Server
 * Properties mirrors, device-owned segment writes, publisher consents and the local
 * memory for anonymous consent decisions.
 *
 * Two unrelated "consents" live here — do not conflate them:
 *  - [hasConsent] is the **CMP** gate (`enableCdp` flag + personalization consent).
 *    It gates identity resolve, links, segment writes and profile updates.
 *  - [trackCdpConsent] / [getCdpConsent] / [hasCdpConsent] are **publisher** consents
 *    (a privacy policy, a newsletter opt-in) recorded in the CDP. They are gated only
 *    on the `enableCdp` flag, never on CMP consent.
 */
internal class CdpManager(
	private val isEnabled: () -> Boolean,
	private val api: CdpApiClient,
	private val accountId: () -> String?,
	private val getMasterId: () -> String?,
	private val writeMasterId: (String) -> String?,
	private val clearMasterId: () -> Unit,
	private val getUserId: () -> String,
	private val getCachedIdentity: (sessionId: String) -> CdpCachedIdentity?,
	private val setCachedIdentity: (rfv: CdpRfv?, cohorts: List<Int>, sessionId: String) -> Unit,
	private val clearCachedIdentity: () -> Unit,
	private val getConsent: () -> Boolean?,
	private val getSessionId: () -> String,
	private val segmentsStore: CdpSegmentsStore,
	private val serverSegmentsStore: CdpServerSegmentsStore,
	private val serverPropertiesStore: CdpServerPropertiesStore,
	private val consentMemory: CdpConsentMemoryStore,
	/** The legacy (`useg`) device-owned segments; either device store marks an assertion. */
	private val getOwnedSegments: () -> List<String> = { emptyList() },
	private val timezone: () -> String? = { TimeZone.getDefault().id },
	private val clock: () -> Long = System::currentTimeMillis,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
	private val gson = Gson()
	private val mutex = Mutex()

	@Volatile
	private var identityResolution: Deferred<Unit>? = null

	@Volatile
	private var memoSessionId: String? = null

	@Volatile
	private var consentReplay: Deferred<Unit>? = null

	/** Bumped by [clearIdentity]; an identity call that started before a reset drops its result. */
	private val generation = AtomicInteger(0)

	/** The generation the current resolve memo belongs to; a reset invalidates it under the lock. */
	private var resolvedGeneration = 0

	@Volatile
	private var identityResolved = false
	private var oneShotCallback: (() -> Unit)? = null

	/** Server Segments for the current master: `server − owned`, committed only through [setServerSegments]. */
	@Volatile
	var serverSegments: List<String> = emptyList()
		private set

	/** Server Properties for the current master, values always strings. */
	@Volatile
	var serverProperties: Map<String, String> = emptyMap()
		private set

	/**
	 * True only after *this* process round-tripped a resolve or link that returned a
	 * master_id **and** mirrored its segments/properties. A warm cache never counts.
	 */
	@Volatile
	var identityFresh: Boolean = false
		private set

	var onMasterIdChanged: ((oldId: String?, newId: String) -> Unit)? = null

	/** Fired by [clearIdentity] with the bucket being wiped, so owners of other mid-scoped stores (meters) can follow. */
	var onIdentityCleared: ((account: String?, previousMid: String) -> Unit)? = null

	/** The CMP gate: `enableCdp` flag **and** personalization consent (unknown allows). */
	fun hasConsent(): Boolean = isEnabled() && getConsent() != false

	fun isEnabled(): Boolean = isEnabled.invoke()

	fun currentAccountId(): String? = accountId()

	fun currentMasterId(): String? = getMasterId()

	private fun getStorageMid(): String = getMasterId() ?: LOCAL_MID_SENTINEL

	private fun numericSiteId(): Long? = accountId()?.toLongOrNull()

	// region identity

	/**
	 * Register a one-shot that fires when identity is ready (enabled + consent +
	 * master_id). It re-arms once per session and after [clearIdentity].
	 */
	fun onIdentityResolved(callback: () -> Unit) {
		oneShotCallback = callback
		maybeFireOneShot()
	}

	private fun maybeFireOneShot() {
		if (identityResolved) return
		if (hasConsent() && getMasterId() != null) {
			identityResolved = true
			oneShotCallback?.invoke()
		}
	}

	suspend fun resolveIdentity() {
		if (!hasConsent()) return
		val currentSession = getSessionId()

		val deferred = mutex.withLock {
			val currentGeneration = generation.get()
			if (currentSession != memoSessionId || currentGeneration != resolvedGeneration) {
				identityResolution = null
				identityResolved = false
				memoSessionId = currentSession
				resolvedGeneration = currentGeneration
			}
			identityResolution ?: scope.async { runResolve(currentSession) }
				.also { identityResolution = it }
		}
		deferred.await()
	}

	private suspend fun runResolve(currentSession: String) {
		try {
			doResolve(currentSession)
		} catch (_: Exception) {
			// fail-open: a broken store must never take the resolve memo down with it
			mutex.withLock { identityResolution = null }
		}
	}

	private suspend fun doResolve(currentSession: String) {
		val startGeneration = generation.get()
		val masterId = getMasterId()

		// Returning visitor within this session: identity already known. Only skip the
		// network when both mirrors have an entry (even an empty one) — a miss means
		// this master was never mirrored on this device.
		if (getCachedIdentity(currentSession) != null && masterId != null) {
			val account = accountId()
			val segments = serverSegmentsStore.read(account, masterId)
			val properties = serverPropertiesStore.read(account, masterId)
			if (segments != null && properties != null) {
				setServerSegments(segments)
				setServerProperties(properties)
				maybeFireOneShot()
				return
			}
		}

		val siteId = numericSiteId() ?: return

		val result = api.resolve(
			CdpResolveParams(siteId = siteId, cookieId = getUserId(), masterId = masterId)
		)
		if (!updateState(result, currentSession, startGeneration)) return

		syncServerSegments(result.segments)
		syncServerProperties(result.properties)
		markIdentityFresh(result)

		if (getMasterId() == null) {
			// No identity yet — clear the memo so the next call retries.
			mutex.withLock { identityResolution = null }
		}
	}

	/**
	 * Called after the mirrors are synced, never inside [updateState]: a beacon in
	 * between would claim freshness while `useg` / `uvar` still lacked the server data.
	 * A missing master_id means the request failed into [UNKNOWN_CDP_IDENTITY].
	 */
	private fun markIdentityFresh(result: CdpIdentityResponse) {
		if (!result.masterId.isNullOrEmpty()) identityFresh = true
	}

	/**
	 * The generation is pinned **before** the resolve: a [clearIdentity] that lands while
	 * the link waits behind a resolve cancels the link instead of re-identifying the
	 * visitor the reset just deleted.
	 */
	suspend fun linkIdentity(type: String, value: String, isDeterministic: Boolean) {
		if (!hasConsent()) return
		val startGeneration = generation.get()
		resolveIdentity()
		if (generation.get() != startGeneration) return
		val siteId = numericSiteId() ?: return

		val result = api.link(
			CdpLinkParams(
				siteId = siteId,
				idType = type,
				idValue = value,
				isDeterministic = isDeterministic,
				masterId = getMasterId()
			)
		)
		if (!updateState(result, getSessionId(), startGeneration)) return

		syncServerSegments(result.segments)
		syncServerProperties(result.properties)
		markIdentityFresh(result)
	}

	/**
	 * Unlinks [type] (every identity of that type when [value] is null) from the
	 * current master. Refreshes the Server Segments / Properties like a link, but does
	 * **not** mark the identity fresh — a delete resolves no identity.
	 */
	suspend fun deleteIdentity(type: String, value: String?) {
		if (!hasConsent()) return
		val startGeneration = generation.get()
		resolveIdentity()
		if (generation.get() != startGeneration) return
		val masterId = getMasterId() ?: return
		val siteId = numericSiteId() ?: return

		val result = api.delete(
			CdpDeleteParams(siteId = siteId, masterId = masterId, idType = type, idValue = value)
		) ?: return
		if (!updateState(result.identity, getSessionId(), startGeneration)) return

		syncServerSegments(result.identity.segments)
		syncServerProperties(result.identity.properties)
	}

	suspend fun onConsentChanged() {
		resolveIdentity()
		maybeFireOneShot()
	}

	/**
	 * Single write path: persist any returned master_id, always cache rfv/cohorts.
	 *
	 * [startGeneration] is the [generation] captured before the network call. A
	 * [clearIdentity] that landed in between means the response belongs to the
	 * signed-out visitor: it is dropped and `false` returned, so no caller can write the
	 * old master back and silently undo a `resetUser()`.
	 */
	private fun updateState(result: CdpIdentityResponse, currentSession: String, startGeneration: Int): Boolean {
		if (generation.get() != startGeneration) return false

		val masterId = result.masterId
		if (!masterId.isNullOrEmpty()) {
			val old = writeMasterId(masterId)
			val account = accountId()
			transferCdpSegments(account, old, masterId)
			dropServerSegments(account, old, masterId)
			dropServerProperties(account, old, masterId)
			if (old != masterId) onMasterIdChanged?.invoke(old, masterId)
		}
		setCachedIdentity(result.rfv, result.cohorts, currentSession)
		maybeFireOneShot()
		return true
	}

	/**
	 * The local wipe behind `resetUser()`. Synchronous — SharedPreferences I/O is — so
	 * a profile read landing right after cannot hand a beacon the logged-out master.
	 * Cached rfv/cohorts are cleared to **absent**, not empty: a false cache hit would
	 * stop the next [resolveIdentity] from minting.
	 */
	fun clearIdentity() {
		val account = accountId()
		val previous = getMasterId()

		generation.incrementAndGet()
		clearMasterId()
		identityResolution = null
		memoSessionId = null
		identityResolved = false
		identityFresh = false
		clearCachedIdentity()
		setServerSegments(emptyList())
		setServerProperties(emptyMap())

		val previousMid = previous ?: segmentsStore.getActiveMid(account) ?: LOCAL_MID_SENTINEL
		try {
			segmentsStore.clear(account, previousMid)
			serverSegmentsStore.clear(account, previousMid)
			serverPropertiesStore.clear(account, previousMid)
			consentMemory.clear(account)
			segmentsStore.setActiveMid(account, LOCAL_MID_SENTINEL)
		} catch (_: Exception) {
			// the master_id clear has already committed; storage failures are best effort
		}
		try {
			onIdentityCleared?.invoke(account, previousMid)
		} catch (_: Exception) {
		}
	}

	/**
	 * `POST /cdp/identity/reset/`. On native there are no server-held cookies to
	 * expire, so this is parity plumbing; null on failure and inert when disabled.
	 */
	suspend fun resetRemoteIdentity(): CdpResetResponse? {
		if (!isEnabled()) return null
		val siteId = numericSiteId() ?: return null
		return api.reset(siteId)
	}

	// endregion

	// region server segments / properties

	/**
	 * Persists `server − owned`, where `owned` spans both device-side stores (the legacy
	 * `useg` list and `cdpsegs_`) since either marks an assertion. Keeping backend keys
	 * out of those stores is what stops `clearSegments` / `replaceSegments` from
	 * emitting `segments_remove` for a membership this device never asserted.
	 */
	fun syncServerSegments(segments: List<String>?) {
		val masterId = getMasterId() ?: return
		val account = accountId()
		val server = segments ?: emptyList()
		val owned = HashSet<String>().apply {
			addAll(getOwnedSegments())
			addAll(segmentsStore.read(account, masterId))
		}

		setServerSegments(server.filterNot { it in owned })
		try {
			serverSegmentsStore.write(account, masterId, serverSegments)
		} catch (_: Exception) {
		}
	}

	fun syncServerProperties(properties: Map<String, String>?) {
		val masterId = getMasterId() ?: return
		setServerProperties(properties ?: emptyMap())
		try {
			serverPropertiesStore.write(accountId(), masterId, serverProperties)
		} catch (_: Exception) {
		}
	}

	/** The single commit point for the diff, so every write path lands in one place. */
	private fun setServerSegments(next: List<String>) {
		serverSegments = next.toList()
	}

	private fun setServerProperties(next: Map<String, String>) {
		serverProperties = LinkedHashMap(next)
	}

	/**
	 * Removes prune eagerly; adds don't need to, since the next resolve recomputes
	 * `server − owned`. Nothing self-corrects a stale membership otherwise: leaving the
	 * key here keeps `useg` sending it for up to a session after the backend dropped it.
	 */
	private fun pruneServerSegments(keep: (String) -> Boolean) {
		val next = serverSegments.filter(keep)
		if (next.size == serverSegments.size) return

		setServerSegments(next)
		val masterId = getMasterId() ?: return
		try {
			serverSegmentsStore.write(accountId(), masterId, next)
		} catch (_: Exception) {
		}
	}

	/**
	 * Deliberately **not** carried over the way [transferCdpSegments] carries the
	 * device-owned mirror: this is a snapshot of one master's backend state. On a merge
	 * the winner's own resolve returns the merged set; on a reset the point is a fresh
	 * user. Carrying it would assert stale memberships into `useg`.
	 */
	private fun dropServerSegments(account: String?, oldId: String?, newId: String) {
		try {
			if (oldId != null && oldId != newId) serverSegmentsStore.clear(account, oldId)
			serverSegmentsStore.cleanupExpired(account, newId)
		} catch (_: Exception) {
		}
	}

	private fun dropServerProperties(account: String?, oldId: String?, newId: String) {
		try {
			if (oldId != null && oldId != newId) serverPropertiesStore.clear(account, oldId)
			serverPropertiesStore.cleanupExpired(account, newId)
		} catch (_: Exception) {
		}
	}

	/** Resolves first (CMP-gated), then reads the mirror. */
	suspend fun getServerSegments(): List<String> {
		resolveIdentity()
		return serverSegments
	}

	suspend fun getServerProperties(): Map<String, String> {
		resolveIdentity()
		return serverProperties
	}

	// endregion

	// region profile

	suspend fun updateProfile(properties: List<Pair<String, String>>) {
		if (!hasConsent()) return
		val masterId = getMasterId() ?: return
		val siteId = numericSiteId() ?: return
		if (properties.isEmpty()) return
		val startGeneration = generation.get()

		val result = api.update(
			CdpProfileUpdateParams(
				siteId = siteId,
				masterId = masterId,
				properties = properties.associate { it.first to it.second }
			)
		)
		updateState(result, getSessionId(), startGeneration)
	}

	fun getUserProfile(serialized: Boolean = false): CdpData {
		if (!isEnabled()) return CdpData(masterId = null, rfv = null, cohorts = emptyList())

		val masterId = getMasterId()
		val cached = getCachedIdentity(getSessionId())
		val rfv = cached?.rfv
		val cohorts = cached?.cohorts ?: emptyList()
		val fresh = identityFresh

		if (!serialized) return CdpData(masterId, rfv, cohorts, identityFresh = fresh)

		return try {
			CdpData(
				masterId = masterId,
				rfv = rfv,
				cohorts = cohorts,
				rfvSerialized = rfv?.let { gson.toJson(it) } ?: "",
				cohortsSerialized = gson.toJson(cohorts),
				identityFresh = fresh
			)
		} catch (_: Exception) {
			CdpData(masterId, rfv, cohorts, rfvSerialized = "", cohortsSerialized = "[]", identityFresh = fresh)
		}
	}

	// endregion

	// region device-owned segments

	fun getCdpSegments(): List<String> {
		if (!isEnabled()) return emptyList()
		return segmentsStore.read(accountId(), getStorageMid())
	}

	suspend fun addSegment(segment: String) {
		if (!isEnabled()) return
		val account = accountId()
		val mid = getStorageMid()
		val local = segmentsStore.read(account, mid)
		if (segment in local) return
		segmentsStore.write(account, mid, local + segment)
		postSegmentChange(segmentsAdd = listOf(segment))
	}

	/** Posts `segments_remove` unconditionally — an explicit remove also deletes a backend-owned membership. */
	suspend fun removeSegment(segment: String) {
		if (!isEnabled()) return
		val account = accountId()
		val mid = getStorageMid()
		val local = segmentsStore.read(account, mid)
		segmentsStore.write(account, mid, local - segment)
		postSegmentChange(segmentsRemove = listOf(segment))
		pruneServerSegments { it != segment }
	}

	/** Posts `segments_remove` for the locally-asserted keys only. */
	suspend fun clearSegments() {
		if (!isEnabled()) return
		val account = accountId()
		val mid = getStorageMid()
		val previous = segmentsStore.read(account, mid)
		segmentsStore.write(account, mid, emptyList())
		pruneServerSegments { false }
		if (previous.isNotEmpty()) postSegmentChange(segmentsRemove = previous)
	}

	suspend fun replaceSegments(segments: List<String>) {
		if (!isEnabled()) return
		val account = accountId()
		val mid = getStorageMid()
		val deduped = segments.distinct()
		val previous = segmentsStore.read(account, mid)
		segmentsStore.write(account, mid, deduped)

		val adds = deduped.filter { it !in previous }
		val removes = previous.filter { it !in deduped }
		if (adds.isNotEmpty() || removes.isNotEmpty()) {
			postSegmentChange(
				segmentsAdd = adds.ifEmpty { null },
				segmentsRemove = removes.ifEmpty { null }
			)
		}
	}

	suspend fun reconcileSegments() {
		if (!hasConsent()) return
		val masterId = getMasterId() ?: return
		val local = segmentsStore.read(accountId(), masterId)
		if (local.isNotEmpty()) {
			postSegmentChange(segmentsAdd = local)
		}
	}

	/**
	 * Bridge the legacy (`useg`) segment store into the CDP store on first identity
	 * resolve, mirroring the web `mergeLegacySegmentsIntoCdpStorage` union. Returns the
	 * merged union so the caller can write it back to the legacy store, keeping both
	 * stores in sync (the web equally re-writes the permanent cookie). No-op without a
	 * real master_id — the caller then keeps the legacy list untouched.
	 */
	fun mergeLegacySegments(legacy: List<String>): List<String> {
		if (!isEnabled()) return legacy
		val masterId = getMasterId() ?: return legacy
		val account = accountId()
		val stored = segmentsStore.read(account, masterId)
		val merged = (stored + legacy).distinct()
		if (merged != stored) segmentsStore.write(account, masterId, merged)
		return merged
	}

	private suspend fun postSegmentChange(
		segmentsAdd: List<String>? = null,
		segmentsRemove: List<String>? = null
	) {
		if (!hasConsent()) return
		val masterId = getMasterId() ?: return
		val siteId = numericSiteId() ?: return
		val startGeneration = generation.get()

		val result = api.update(
			CdpProfileUpdateParams(
				siteId = siteId,
				masterId = masterId,
				segmentsAdd = segmentsAdd,
				segmentsRemove = segmentsRemove
			)
		)
		updateState(result, getSessionId(), startGeneration)
	}

	internal fun transferCdpSegments(account: String?, oldId: String?, newId: String) {
		if (account.isNullOrEmpty()) return
		try {
			val previousMid = oldId ?: segmentsStore.getActiveMid(account) ?: LOCAL_MID_SENTINEL
			if (previousMid == newId) {
				segmentsStore.setActiveMid(account, newId)
				return
			}
			val previous = segmentsStore.read(account, previousMid)
			val current = segmentsStore.read(account, newId)
			val union = (current + previous).distinct()
			if (union.isNotEmpty()) segmentsStore.write(account, newId, union)
			segmentsStore.clear(account, previousMid)
			segmentsStore.setActiveMid(account, newId)
			segmentsStore.cleanupExpired(account, newId)
		} catch (_: Exception) {

		}
	}

	// endregion

	// region publisher consents

	/**
	 * `POST /cdp/consents/record/`. Deliberately **not** gated on CMP consent, unlike
	 * every sibling: a visitor who declines tracking and then accepts the privacy policy
	 * has still accepted it, and failing to record that destroys the proof this feature
	 * exists to keep. Every call is sent — no client-side dedupe.
	 */
	suspend fun trackCdpConsent(decision: CdpConsent): CdpConsentRecordResponse? {
		if (!isEnabled()) return null
		if (decision.consentId.isEmpty()) return null
		val siteId = numericSiteId() ?: return null

		val masterId = getMasterId()
		val startGeneration = generation.get()
		var params = CdpConsentRecordParams(
			siteId = siteId,
			masterId = masterId,
			consentId = decision.consentId,
			consentVersionId = decision.versionId,
			status = decision.status,
			metadata = decision.metadata ?: emptyMap(),
			timezone = timezone()
		)
		decision.email?.takeIf { it.isNotEmpty() }?.let { email ->
			val subject = consentEmailIdentity(email)
			params = params.copy(idType = subject.idType, idValue = subject.idValue)
		}

		val result = api.recordConsent(params)

		adoptCanonicalMaster(masterId, result, startGeneration)

		if (result != null && result.recorded && masterId == null) {
			try {
				consentMemory.remember(
					accountId(),
					decision.consentId,
					CdpRememberedConsentDecision(
						versionId = decision.versionId,
						status = decision.status,
						ts = clock()
					)
				)
			} catch (_: Exception) {
				// a storage failure never fails the record
			}
		}

		return result
	}

	/**
	 * A record that came back under a different master was merged into that winner
	 * server-side. Adopted through the link response's own path ([updateState]),
	 * keeping the cached rfv/cohorts until the next resolve refreshes them; never
	 * without a master of our own (the decision is remembered instead).
	 */
	private fun adoptCanonicalMaster(sent: String?, result: CdpConsentRecordResponse?, startGeneration: Int) {
		val returned = result?.masterId
		if (sent == null || returned.isNullOrEmpty() || returned == sent || !isValidUuid(returned)) return

		val session = getSessionId()
		val cached = getCachedIdentity(session)
		updateState(
			CdpIdentityResponse(masterId = returned, rfv = cached?.rfv, cohorts = cached?.cohorts ?: emptyList()),
			session,
			startGeneration
		)
	}

	/**
	 * Re-records every decision remembered under the `local` bucket now that a master
	 * exists, then forgets only those the server answered `recorded: true`. Memoised
	 * while in flight, re-armed on settle.
	 */
	suspend fun replayConsentDecisions() {
		val deferred = mutex.withLock {
			consentReplay ?: scope.async {
				try {
					runConsentReplay()
				} catch (_: Exception) {
					// best effort: the decisions stay remembered for the next resolution
				} finally {
					mutex.withLock { consentReplay = null }
				}
			}.also { consentReplay = it }
		}
		deferred.await()
	}

	private suspend fun runConsentReplay() {
		val masterId = getMasterId()
		if (!isEnabled() || masterId == null) return
		val siteId = numericSiteId() ?: return
		val account = accountId()

		val decisions = try {
			consentMemory.getRemembered(account)
		} catch (_: Exception) {
			emptyMap()
		}
		if (decisions.isEmpty()) return

		val recorded = coroutineScope {
			decisions.map { (consentId, decision) ->
				async {
					val result = api.recordConsent(
						CdpConsentRecordParams(
							siteId = siteId,
							masterId = masterId,
							consentId = consentId,
							consentVersionId = decision.versionId,
							status = decision.status
						)
					)
					consentId.takeIf { result != null && result.recorded }
				}
			}.awaitAll().filterNotNull()
		}

		if (recorded.isNotEmpty()) {
			try {
				consentMemory.forget(account, recorded)
			} catch (_: Exception) {
			}
		}
	}

	/** `GET /cdp/consents/catalog/`. Not CMP-gated: a prompt has to render before consent is known. */
	suspend fun getCdpConsent(ref: CdpConsentRef): CdpConsentDefinition? {
		if (!isEnabled()) return null
		if (ref.consentId.isEmpty()) return null
		val siteId = numericSiteId() ?: return null

		val items = api.fetchConsentCatalog(siteId, ref.consentId, ref.versionId) ?: return null
		return items.firstOrNull()?.let(::toConsentDefinition)
	}

	/** Not public: the SDK exposes only the boolean [hasCdpConsent], which reduces this. */
	internal suspend fun consentCheck(query: CdpConsentQuery): CdpConsentCheck? {
		if (!isEnabled()) return null
		if (query.consentId.isEmpty()) return null

		val masterId = getMasterId()
		val subject = query.email?.takeIf { it.isNotEmpty() }?.let(::consentEmailIdentity)

		if (masterId == null && subject == null) {
			return rememberedConsentStatus(query.consentId, query.versionId)
		}

		val siteId = numericSiteId() ?: return null
		val result = api.fetchConsentStatus(
			CdpConsentCheckParams(
				siteId = siteId,
				consentId = query.consentId,
				consentVersionId = query.versionId,
				masterId = masterId,
				idType = subject?.idType,
				idValue = subject?.idValue
			)
		) ?: return null

		return toConsentStatus(result)
	}

	/** False — never null — on transport errors and when CDP is disabled. */
	suspend fun hasCdpConsent(query: CdpConsentQuery): Boolean {
		if (!isEnabled()) return false
		return consentCheck(query)?.granted == true
	}

	/**
	 * Mirrors the server's rule: with a requested version, only an accept at exactly that
	 * version grants; without one, any accepted version grants and the answered version
	 * is echoed as `versionId`. A storage failure reads as unanswered.
	 */
	private fun rememberedConsentStatus(consentId: String, version: String?): CdpConsentCheck {
		val decisions = try {
			consentMemory.getRemembered(accountId())
		} catch (_: Exception) {
			emptyMap()
		}
		val entry = decisions[consentId]
			?: return CdpConsentCheck(
				masterId = null,
				consentId = consentId,
				versionId = version ?: "",
				granted = false,
				answered = false
			)

		val versionId = version ?: entry.versionId
		return CdpConsentCheck(
			masterId = null,
			consentId = consentId,
			versionId = versionId,
			granted = entry.status == CdpConsentStatus.ACCEPTED && versionId == entry.versionId,
			answered = true,
			status = entry.status,
			answeredVersionId = entry.versionId
		)
	}

	private fun toConsentStatus(result: CdpConsentCheckResponse): CdpConsentCheck =
		CdpConsentCheck(
			masterId = result.masterId?.takeIf { it.isNotEmpty() },
			consentId = result.consentId,
			versionId = result.consentVersionId ?: "",
			granted = result.granted,
			answered = result.answered,
			status = result.status?.takeIf { it.isNotEmpty() }?.let { CdpConsentStatus.fromWire(it) },
			answeredVersionId = result.answeredVersionId?.takeIf { it.isNotEmpty() }
		)

	private fun toConsentDefinition(item: CdpConsentCatalogItem): CdpConsentDefinition =
		CdpConsentDefinition(
			consentId = item.consentId,
			name = item.name,
			purpose = item.purpose,
			mandatory = item.mandatory,
			acceptMethod = item.acceptMethod,
			showPolicy = CdpConsentShowPolicy.fromWire(item.showPolicy),
			version = item.version?.let { v ->
				CdpConsentVersion(
					versionId = v.versionId,
					label = v.label,
					date = v.date,
					displayPrompt = v.displayPrompt,
					errorMessage = v.errorMessage,
					metadata = v.metadata
				)
			}
		)

	// endregion
}
