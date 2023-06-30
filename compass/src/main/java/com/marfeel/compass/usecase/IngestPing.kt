package com.marfeel.compass.usecase

import com.marfeel.compass.core.ping.IngestPingEmitterState
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal class IngestPing(
	override val api: ApiClient,
	override val memory: Memory,
	override val storage: Storage,
) : Ping<IngestPingEmitterState, IngestPingData>(api, memory, storage) {
	override fun invoke(input: IngestPingData) {
		val conversions = memory.readPendingConversions()
		val currentTimeStamp = currentTimeStampInSeconds()

		api.ingestPing(input).also {
			memory.clearTrackedConversions(conversions)
			storage.updateLastPingTimeStamp(currentTimeStamp)
		}
	}

	override fun getData(input: IngestPingEmitterState): IngestPingData? {
		val conversions = memory.readPendingConversions()
		val pingData = getData() ?: return null

		return IngestPingData(
			accountId = pingData.accountId,
			sessionTimeStamp = pingData.sessionTimeStamp,
			url = input.url,
			canonicalUrl = input.url,
			previousUrl = pingData.previousUrl,
			pageId = pingData.pageId,
			originalUserId = pingData.originalUserId,
			sessionId = pingData.sessionId,
			pingCounter = input.pingCounter,
			currentTimeStamp = pingData.currentTimeStamp,
			userType = pingData.userType,
			registeredUserId = pingData.registeredUserId,
			scrollPercent = input.scrollPercent ?: 0,
			firsVisitTimeStamp = pingData.firsVisitTimeStamp,
			previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
			timeOnPage = input.activeTimeOnPage.toInt(),
			pageStartTimeStamp = memory.readPage()?.startTimeStamp ?: 0L,
			conversions = conversions.join(),
			version = pingData.version,
			pageVars = memory.readPageVars(),
			sessionVars = memory.readSessionVars(),
			userVars = storage.readUserVars(),
			userSegments = storage.readUserSegments(),
			pageType = memory.readPageTechnology()!!,
			userConsent = storage.readUserConsent()
		)
	}
}

private fun List<String>.join(): String? =
	if (isEmpty()) {
		null
	} else {
		this.joinToString(",")
	}


