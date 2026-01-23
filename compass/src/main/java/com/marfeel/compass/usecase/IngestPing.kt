package com.marfeel.compass.usecase

import com.marfeel.compass.core.ping.IngestPingEmitterState
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal class IngestPing(
	override val api: ApiClient,
	override val sessionStorage: SessionStorage,
	override val storage: Storage,
) : Ping<IngestPingEmitterState, IngestPingData>(api, sessionStorage, storage) {
	private var tick = 0;

	override fun invoke(input: IngestPingData) {
		val conversions = sessionStorage.readPendingConversions()
		val currentTimeStamp = currentTimeStampInSeconds()

		if (conversions.isEmpty()) {
			api.ingestPing(input.copy(pingCounter = tick++))

		} else {
			conversions.forEach { conversion ->
				val updatedInput = input.copy(conversions = conversion, pingCounter = tick++)
				api.ingestPing(updatedInput)
			}
		}

		sessionStorage.clearTrackedConversions(conversions)
		storage.updateLastPingTimeStamp(currentTimeStamp)
	}

	override fun getData(input: IngestPingEmitterState): IngestPingData? {
		val currentPageId = sessionStorage.readPage()?.pageId
		if (currentPageId != input.pageId) return null

		val pingData = getData() ?: return null

		return IngestPingData(
			accountId = pingData.accountId,
			sessionTimeStamp = pingData.sessionTimeStamp,
			url = input.url,
			canonicalUrl = input.url,
			previousUrl = pingData.previousUrl,
			pageId = input.pageId,
			originalUserId = pingData.originalUserId,
			sessionId = pingData.sessionId,
			pingCounter = null,
			currentTimeStamp = pingData.currentTimeStamp,
			userType = pingData.userType,
			registeredUserId = pingData.registeredUserId,
			scrollPercent = input.scrollPercent ?: 0,
			firsVisitTimeStamp = pingData.firsVisitTimeStamp,
			previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
			timeOnPage = input.activeTimeOnPage.toInt(),
			pageStartTimeStamp = sessionStorage.readPage()?.startTimeStamp ?: 0L,
			conversions = null,
			version = pingData.version,
			pageVars = sessionStorage.readPageVars(),
			sessionVars = sessionStorage.readSessionVars(),
			userVars = storage.readUserVars(),
			userSegments = storage.readUserSegments(),
			pageType = sessionStorage.readPageTechnology()!!,
			userConsent = storage.readUserConsent(),
			landingPage =  sessionStorage.readLandingPage(),
			recirculationSource = sessionStorage.readRecirculationSource(),
			cc = getCc(storage.readUserConsent()),
			pageMetrics = sessionStorage.readPageMetrics()
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

private fun List<String>.join(): String? =
	if (isEmpty()) {
		null
	} else {
		this.joinToString(",")
	}


