package com.marfeel.compass.usecase

import com.marfeel.compass.core.model.multimedia.MultimediaPingData
import com.marfeel.compass.core.ping.MultimediaPingEmitterState
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal class MultimediaPing(
	override val api: ApiClient,
	override val memory: Memory,
	override val storage: Storage,
) : Ping<MultimediaPingEmitterState, MultimediaPingData>(api, memory, storage) {
	override fun invoke(input: MultimediaPingData) {
		api.multimediaPing(input)
	}

	override fun getData(input: MultimediaPingEmitterState): MultimediaPingData? {
		val pingData = getData() ?: return null

		return MultimediaPingData(
			accountId = pingData.accountId,
			sessionTimeStamp = pingData.sessionTimeStamp,
			url = pingData.url,
			canonicalUrl = pingData.canonicalUrl,
			previousUrl = pingData.previousUrl,
			pageId = pingData.pageId,
			originalUserId = pingData.originalUserId,
			sessionId = pingData.sessionId,
			pingCounter = input.pingCounter,
			currentTimeStamp = pingData.currentTimeStamp,
			userType = pingData.userType,
			registeredUserId = pingData.registeredUserId,
			firsVisitTimeStamp = pingData.firsVisitTimeStamp,
			previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
			version = pingData.version,
			item = input.item
		)
	}
}