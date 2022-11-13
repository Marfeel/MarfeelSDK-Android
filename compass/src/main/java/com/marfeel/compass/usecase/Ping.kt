package com.marfeel.compass.usecase

import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.PingData
import com.marfeel.compass.core.PingEmitterState
import com.marfeel.compass.core.UseCase
import com.marfeel.compass.core.currentTimeStampInSeconds
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal class Ping(
	private val api: ApiClient,
	private val memory: Memory,
	private val storage: Storage,
) : UseCase<PingEmitterState, Unit> {
	override fun invoke(input: PingEmitterState) {
		val conversions = memory.readPendingConversions()
		val currentTimeStamp = currentTimeStampInSeconds()
		val currentSession = memory.readSession()
		val pingData = PingData(
			accountId = memory.readAccountId() ?: "",
			sessionTimeStamp = currentSession.timeStamp,
			url = input.url,
			canonicalUrl = input.url,
			previousUrl = memory.readPreviousUrl() ?: "",
			pageId = memory.readPage()?.pageId ?: "",
			originalUserId = storage.readOriginalUserId(),
			sessionId = currentSession.id,
			pingCounter = input.pingCounter,
			currentTimeStamp = currentTimeStamp,
			userType = storage.readUserType(),
			registeredUserId = storage.readRegisteredUserId() ?: "",
			scrollPercent = input.scrollPercent ?: 0,
			firsVisitTimeStamp = storage.readFirstSessionTimeStamp(),
			previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
			timeOnPage = input.activeTimeOnPage.toInt(),
			pageStartTimeStamp = memory.readPage()?.startTimeStamp ?: 0L,
			conversions = conversions.join(),
			version = BuildConfig.VERSION
		)
		api.ping(pingData).also {
			memory.clearTrackedConversions(conversions)
			storage.updateLastPingTimeStamp(currentTimeStamp)
		}
	}
}

private fun List<String>.join(): String? =
	if (isEmpty()) {
		null
	} else {
		this.joinToString(",")
	}


