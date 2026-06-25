package com.marfeel.compass.usecase

import com.marfeel.compass.core.ping.IngestPingEmitterState
import com.marfeel.compass.core.model.compass.ConversionOptions
import com.marfeel.compass.core.model.compass.ConversionScope
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.storage.Conversion
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
				val updatedInput = input.copy(
					conversion = conversion,
					conversionId = getConversionId(conversion.options, input.sessionId, input.pageId),
					pingCounter = tick++
				)
				api.ingestPing(updatedInput)
			}
		}

		sessionStorage.clearTrackedConversions(conversions)
		storage.updateLastPingTimeStamp(currentTimeStamp)
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

	override fun getData(input: IngestPingEmitterState): IngestPingData? {
		val currentPageId = sessionStorage.readPage()?.pageId
		if (currentPageId != input.pageId) return null

		val pingData = getData() ?: return null

		// CDP beacon fields: attached only when personalization consent is present AND
		// a master_id exists (mirrors the web `if (consent) { if (masterId) {...} }`).
		// When CDP is disabled getData() returns a null master_id, so nothing is added.
		val cdpData = CompassComponent.cdpManager.getData(serialized = true)
		val attachCdp = CompassComponent.cdpManager.hasConsent() && cdpData.masterId != null

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
			userVars = storage.readUserVars(),
			userSegments = storage.readUserSegments(),
			pageType = sessionStorage.readPageTechnology()!!,
			userConsent = storage.readUserConsent(),
			landingPage =  sessionStorage.readLandingPage(),
			recirculationSource = sessionStorage.readRecirculationSource(),
			cc = getCc(storage.readUserConsent()),
			pageMetrics = sessionStorage.readPageMetrics(),
			cdpMasterId = if (attachCdp) cdpData.masterId else null,
			cdpRfv = if (attachCdp) cdpData.rfvSerialized.ifEmpty { null } else null,
			cdpCohorts = if (attachCdp) cdpData.cohortsSerialized else null
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
