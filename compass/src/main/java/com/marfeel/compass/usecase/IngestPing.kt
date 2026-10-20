package com.marfeel.compass.usecase

import com.marfeel.compass.core.ping.IngestPingEmitterState
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.storage.Conversion
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.tracker.CompassTracker

/**
 * A page as it was when it was left, kept while conversions tracked on it are pending.
 * Their pings carry it instead of the current page and continue its tick sequence.
 */
private class ClosedPage(val data: IngestPingData, var nextTick: Int)

internal class IngestPing(
	override val api: ApiClient,
	override val sessionStorage: SessionStorage,
	override val storage: Storage,
	/**
	 * What the beacon sends as `useg`: the device-owned segments unioned with the Server
	 * Segments, server first, trimmed to the cap. Injectable so the payload can be
	 * tested without the tracker singleton.
	 */
	private val userSegmentsProvider: () -> List<String> = { CompassTracker.getUserSegments() },
	/** What the beacon sends as `uvar`: device-owned vars plus the Server Properties. */
	private val userVarsProvider: () -> Map<String, String> = { CompassTracker.getUserVars() }
) : Ping<IngestPingEmitterState, IngestPingData>(api, sessionStorage, storage) {
	private var tick = 0;
	private val closedPagesLock = Any()
	private val closedPages = mutableMapOf<String, ClosedPage>()

	/**
	 * One ping per pending conversion no other ping is sending, plus the plain ping unless a
	 * conversion of the current page carries it. A conversion stays queued until its ping
	 * reaches the server, so a failed ping or a page change never drops it.
	 */
	override fun invoke(input: IngestPingData) {
		val currentTimeStamp = currentTimeStampInSeconds()
		var pingedCurrentPage = false

		sessionStorage.claimPendingConversions().forEach { claimed ->
			val conversion = claimed.conversion
			val ping = closedPagePing(conversion, input.pageId, currentTimeStamp)
				?: input.copy(conversion = conversion, conversionId = conversion.id, pingCounter = tick++)
					.also { pingedCurrentPage = true }

			sessionStorage.settleConversion(claimed.key, api.ingestPing(ping))
		}

		if (!pingedCurrentPage) {
			api.ingestPing(input.copy(pingCounter = tick++))
		}

		pruneClosedPages()
		storage.updateLastPingTimeStamp(currentTimeStamp)
	}

	/** The ping of a conversion tracked on a page already left, or null when it belongs to the current one. */
	private fun closedPagePing(conversion: Conversion, currentPageId: String, currentTimeStamp: Long): IngestPingData? =
		synchronized(closedPagesLock) {
			val pageId = conversion.pageId?.takeIf { it != currentPageId } ?: return null
			val page = closedPages[pageId] ?: return null

			page.data.copy(
				currentTimeStamp = currentTimeStamp,
				conversion = conversion,
				conversionId = conversion.id,
				pingCounter = page.nextTick++
			)
		}

	/**
	 * Keeps the page being left while conversions tracked on it are pending. Call it before
	 * the session storage moves to the next page.
	 */
	fun closePage(input: IngestPingEmitterState) {
		if (!sessionStorage.hasPendingConversions(input.pageId)) return
		val data = getData(input) ?: return

		synchronized(closedPagesLock) {
			closedPages.getOrPut(input.pageId) { ClosedPage(data, tick) }
		}
	}

	private fun pruneClosedPages() = synchronized(closedPagesLock) {
		closedPages.keys.retainAll { sessionStorage.hasPendingConversions(it) }
	}

	override fun getData(input: IngestPingEmitterState): IngestPingData? {
		val currentPageId = sessionStorage.readPage()?.pageId
		if (currentPageId != input.pageId) return null

		val pingData = getData(userVars = userVarsProvider(), userSegments = userSegmentsProvider()) ?: return null
		// When CDP is disabled getUserProfile() returns a null master_id, so nothing is added.
		val cdpManager = CompassComponent.cdpManager
		val cdpData = cdpManager.getUserProfile(serialized = true)
		val attachCdp = cdpManager.hasConsent() && cdpData.masterId != null

		return IngestPingData(
			accountId = pingData.accountId,
			sessionTimeStamp = pingData.sessionTimeStamp,
			url = input.url,
			canonicalUrl = input.url,
			previousUrl = pingData.previousUrl,
			pageId = input.pageId,
			originalUserId = pingData.originalUserId,
			sessionId = input.sessionId,
			pingCounter = null,
			currentTimeStamp = pingData.currentTimeStamp,
			userType = pingData.userType,
			registeredUserId = pingData.registeredUserId,
			scrollPercent = input.scrollPercent ?: 0,
			firsVisitTimeStamp = pingData.firsVisitTimeStamp,
			previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
			timeOnPage = input.activeTimeOnPage.toInt(),
			pageStartTimeStamp = sessionStorage.readPage()?.startTimeStamp ?: 0L,
			version = pingData.version,
			pageVars = sessionStorage.readPageVars(),
			sessionVars = sessionStorage.readSessionVars(),
			userVars = pingData.userVars,
			userSegments = pingData.userSegments,
			pageType = sessionStorage.readPageTechnology()!!,
			userConsent = storage.readUserConsent(),
			landingPage =  sessionStorage.readLandingPage(),
			recirculationSource = sessionStorage.readRecirculationSource(),
			cc = getCc(storage.readUserConsent()),
			pageMetrics = sessionStorage.readPageMetrics(),
			cdpMasterId = if (attachCdp) cdpData.masterId else null,
			cdpRfv = if (attachCdp) cdpData.rfvSerialized.ifEmpty { null } else null,
			cdpCohorts = if (attachCdp) cdpData.cohortsSerialized else null,
			cdpFresh = if (attachCdp && cdpData.identityFresh) "1" else null
		)
	}

	private fun getCc(userConsent: Boolean?): Int = when (userConsent) {
			true -> 1
			false -> 0
			null -> 3
		}


	fun resetPing() {
		tick = 0
	}

}
