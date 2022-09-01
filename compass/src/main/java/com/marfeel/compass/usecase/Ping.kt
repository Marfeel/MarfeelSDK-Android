package com.marfeel.compass.usecase

import android.util.Log
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
	override fun invoke(pingEmitterState: PingEmitterState) {
		val conversions = memory.readPendingConversions()
		val currentTimeStamp = currentTimeStampInSeconds()
		val currentSession = memory.readSession()
		val pingData = PingData(
			accountId = memory.readAccountId() ?: "",
			sessionTimeStamp = currentSession.timeStamp,
			url = pingEmitterState.url,
			previousUrl = memory.readPreviousUrl() ?: "",
			pageId = memory.readPage()?.pageId ?: "",
			originalUserId = storage.readOriginalUserId(),
			sessionId = currentSession.id,
			pingCounter = pingEmitterState.pingCounter,
			currentTimeStamp = currentTimeStamp,
			userType = storage.readUserType(),
			registeredUserId = storage.readRegisteredUserId() ?: "",
			scrollPercent = pingEmitterState.scrollPercent ?: 0,
			firsVisitTimeStamp = storage.readFirstSessionTimeStamp(),
			previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
			timeOnPage = pingEmitterState.activeTimeOnPage.toInt(),
			pageStartTimeStamp = memory.readPage()?.startTimeStamp ?: 0L,
			conversions = conversions.join()
		)
		api.ping(pingData).also {
			memory.clearTrackedConversions(conversions)
			storage.updateLastPingTimeStamp(currentTimeStamp)
		}
		Log.d("Compass", "ping \n scrollPercentage: ${pingEmitterState.scrollPercent} \n")
	}
}

private fun List<String>.join(): String? =
	if (isEmpty()) {
		null
	} else {
		this.joinToString(",")
	}


