package com.marfeel.compass.usecase

import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.ConversionOptions
import com.marfeel.compass.core.ConversionScope
import com.marfeel.compass.core.PingData
import com.marfeel.compass.core.PingEmitterState
import com.marfeel.compass.core.UseCase
import com.marfeel.compass.core.currentTimeStampInSeconds
import com.marfeel.compass.memory.Conversion
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
		val currentPage = memory.readPage()
		val conversionOptions = conversions.firstNotNullOfOrNull { it.options }
		val pingData = PingData(
			accountId = memory.readAccountId() ?: "",
			sessionTimeStamp = currentSession.timeStamp,
			url = input.url,
			canonicalUrl = input.url,
			previousUrl = memory.readPreviousUrl() ?: "",
			pageId = currentPage?.pageId ?: "",
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
			pageStartTimeStamp = currentPage?.startTimeStamp ?: 0L,
			conversions = conversions.joinNames(),
			conversionInitiator = conversionOptions?.initiator,
			conversionId = getConversionId(conversionOptions, currentSession.id, currentPage?.pageId),
			conversionValue = conversionOptions?.value,
			conversionMeta = conversionOptions?.meta?.toMetaArray(),
			version = BuildConfig.VERSION
		)
		api.ping(pingData).also {
			memory.clearTrackedConversions(conversions)
			storage.updateLastPingTimeStamp(currentTimeStamp)
		}
	}

	private fun getConversionId(
		options: ConversionOptions?,
		sessionId: String,
		pageId: String?
	): String? {
		if (options == null) return null
		if (options.id != null) return options.id
		return when (options.scope) {
			ConversionScope.User -> storage.readRegisteredUserId()
			ConversionScope.Session -> sessionId
			ConversionScope.Page -> pageId
			null -> null
		}
	}
}

private fun List<Conversion>.joinNames(): String? =
	if (isEmpty()) {
		null
	} else {
		this.joinToString(",") { it.name }
	}

private fun Map<String, String>.toMetaArray(): List<List<String>> =
	this.map { (key, value) -> listOf(key, value) }


