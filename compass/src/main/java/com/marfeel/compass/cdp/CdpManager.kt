package com.marfeel.compass.cdp

import com.google.gson.Gson
import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpData
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpResolveParams
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.cdp.store.CdpSegmentsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CdpManager(
	private val isEnabled: () -> Boolean,
	private val api: CdpApiClient,
	private val accountId: () -> String?,
	private val getMasterId: () -> String?,
	private val writeMasterId: (String) -> String?,
	private val getUserId: () -> String,
	private val getCachedIdentity: (sessionId: String) -> CdpCachedIdentity?,
	private val setCachedIdentity: (rfv: CdpRfv?, cohorts: List<Int>, sessionId: String) -> Unit,
	private val getConsent: () -> Boolean?,
	private val getSessionId: () -> String,
	private val segmentsStore: CdpSegmentsStore,
	private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
	private val gson = Gson()
	private val mutex = Mutex()

	private var identityResolution: Deferred<Unit>? = null
	private var memoSessionId: String? = null

	@Volatile
	private var identityResolved = false
	private var oneShotCallback: (() -> Unit)? = null

	var onMasterIdChanged: ((oldId: String?, newId: String) -> Unit)? = null

	fun hasConsent(): Boolean = isEnabled() && getConsent() != false

	fun currentAccountId(): String? = accountId()

	fun currentMasterId(): String? = getMasterId()

	private fun getStorageMid(): String = getMasterId() ?: LOCAL_MID_SENTINEL

	private fun numericSiteId(): Long? = accountId()?.toLongOrNull()

	/**
	 * Register a one-shot that fires when identity is ready (enabled + consent +
	 * master_id). It re-arms once per session
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
			if (currentSession != memoSessionId) {
				identityResolution = null
				identityResolved = false
				memoSessionId = currentSession
			}
			identityResolution ?: scope.async { runResolve(currentSession) }
				.also { identityResolution = it }
		}
		deferred.await()
	}

	private suspend fun runResolve(currentSession: String) {
		// Returning visitor within this session: identity already known, nothing to do.
		if (getCachedIdentity(currentSession) != null && getMasterId() != null) {
			maybeFireOneShot()
			return
		}

		val siteId = numericSiteId()
		if (siteId == null) {
			return
		}

		val result = api.resolve(
			CdpResolveParams(siteId = siteId, cookieId = getUserId(), masterId = getMasterId())
		)
		updateState(result, currentSession)

		if (getMasterId() == null) {
			// No identity yet — clear the memo so the next call retries.
			mutex.withLock { identityResolution = null }
		}
	}

	suspend fun linkIdentity(type: String, value: String, isDeterministic: Boolean) {
		if (!hasConsent()) return
		resolveIdentity()
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
		updateState(result, getSessionId())
	}

	suspend fun onConsentChanged() {
		resolveIdentity()
		maybeFireOneShot()
	}

	/** Single write path: persist any returned master_id, always cache rfv/cohorts. */
	private fun updateState(result: CdpIdentityResponse, currentSession: String) {
		val masterId = result.masterId
		if (!masterId.isNullOrEmpty()) {
			val old = writeMasterId(masterId)
			transferCdpSegments(accountId(), old, masterId)
			if (old != masterId) onMasterIdChanged?.invoke(old, masterId)
		}
		setCachedIdentity(result.rfv, result.cohorts, currentSession)
		maybeFireOneShot()
	}

	suspend fun updateProfile(properties: List<Pair<String, String>>) {
		if (!hasConsent()) return
		val masterId = getMasterId() ?: return
		val siteId = numericSiteId() ?: return
		if (properties.isEmpty()) return

		val result = api.update(
			CdpProfileUpdateParams(
				siteId = siteId,
				masterId = masterId,
				properties = properties.associate { it.first to it.second }
			)
		)
		updateState(result, getSessionId())
	}

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

	suspend fun removeSegment(segment: String) {
		if (!isEnabled()) return
		val account = accountId()
		val mid = getStorageMid()
		val local = segmentsStore.read(account, mid)
		segmentsStore.write(account, mid, local - segment)
		postSegmentChange(segmentsRemove = listOf(segment))
	}

	suspend fun clearSegments() {
		if (!isEnabled()) return
		val account = accountId()
		val mid = getStorageMid()
		val previous = segmentsStore.read(account, mid)
		segmentsStore.write(account, mid, emptyList())
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

	private suspend fun postSegmentChange(
		segmentsAdd: List<String>? = null,
		segmentsRemove: List<String>? = null
	) {
		if (!hasConsent()) return
		val masterId = getMasterId() ?: return
		val siteId = numericSiteId() ?: return

		val result = api.update(
			CdpProfileUpdateParams(
				siteId = siteId,
				masterId = masterId,
				segmentsAdd = segmentsAdd,
				segmentsRemove = segmentsRemove
			)
		)
		updateState(result, getSessionId())
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

	fun getData(serialized: Boolean = false): CdpData {
		if (!isEnabled()) return CdpData(masterId = null, rfv = null, cohorts = emptyList())

		val masterId = getMasterId()
		val cached = getCachedIdentity(getSessionId())
		val rfv = cached?.rfv
		val cohorts = cached?.cohorts ?: emptyList()

		if (!serialized) return CdpData(masterId, rfv, cohorts)

		return try {
			CdpData(
				masterId = masterId,
				rfv = rfv,
				cohorts = cohorts,
				rfvSerialized = rfv?.let { gson.toJson(it) } ?: "",
				cohortsSerialized = gson.toJson(cohorts)
			)
		} catch (_: Exception) {
			CdpData(masterId, rfv, cohorts, rfvSerialized = "", cohortsSerialized = "[]")
		}
	}
}
