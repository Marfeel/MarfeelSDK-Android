package com.marfeel.compass.core.model.compass

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.registerPingDataSerializer
import com.marfeel.compass.storage.Conversion
import java.lang.reflect.Type
import kotlin.jvm.Transient

internal class IngestPingData(
	accountId: String,
	sessionTimeStamp: Long,
	url: String,
	canonicalUrl: String,
	previousUrl: String,
	pageId: String,
	originalUserId: String,
	sessionId: String,
	pingCounter: Int?,
	currentTimeStamp: Long,
	userType: UserType,
	registeredUserId: String,
	pageVars: Map<String, String>,
	sessionVars: Map<String, String>,
	userVars: Map<String, String>,
	userSegments: List<String>,
	@SerializedName("sc")
	val scrollPercent: Int,
	firsVisitTimeStamp: Long,
	previousSessionTimeStamp: Long?,
	@SerializedName("l")
	val timeOnPage: Int,
	@SerializedName("ps")
	val pageStartTimeStamp: Long,
	@Transient
	val conversion: Conversion? = null,
	@SerializedName("cvid")
	val conversionId: String? = null,
	version: String,
	pageType: Int,
	userConsent: Boolean?,
	@SerializedName("lp")
	val landingPage: String?,
	@SerializedName("rs")
	val recirculationSource: String?,
	@SerializedName("cc")
	val cc: Int,
	@SerializedName("pm")
	val pageMetrics: Map<String, Int>,
	@SerializedName("cdp_mid")
	val cdpMasterId: String? = null,
	@SerializedName("cdp_rfv")
	val cdpRfv: String? = null,
	@SerializedName("cdp_cohorts")
	val cdpCohorts: String? = null
): PingData(
	accountId,
	sessionTimeStamp,
	url,
	canonicalUrl,
	previousUrl,
	pageId,
	originalUserId,
	sessionId,
	userType,
	registeredUserId,
	firsVisitTimeStamp,
	previousSessionTimeStamp,
	version,
	currentTimeStamp,
	pingCounter,
	userVars,
	pageVars,
	sessionVars,
	userSegments,
	pageType = pageType,
	userConsent = userConsent
) {
	fun copy(
		accountId: String = this.accountId,
		sessionTimeStamp: Long = this.sessionTimeStamp,
		url: String = this.url,
		canonicalUrl: String = this.canonicalUrl,
		previousUrl: String = this.previousUrl,
		pageId: String = this.pageId,
		originalUserId: String = this.originalUserId,
		sessionId: String = this.sessionId,
		userType: UserType = this.userType,
		registeredUserId: String = this.registeredUserId,
		firsVisitTimeStamp: Long = this.firsVisitTimeStamp,
		previousSessionTimeStamp: Long? = this.previousSessionTimeStamp,
		version: String = this.version,
		currentTimeStamp: Long = this.currentTimeStamp,
		pingCounter: Int? = this.pingCounter,
		userVars: Map<String, String> = this.userVars,
		pageVars: Map<String, String> = this.pageVars,
		sessionVars: Map<String, String> = this.sessionVars,
		userSegments: List<String> = this.userSegments,
		pageType: Int = this.pageType,
		userConsent: Boolean? = this.userConsent,
		scrollPercent: Int = this.scrollPercent,
		timeOnPage: Int = this.timeOnPage,
		pageStartTimeStamp: Long = this.pageStartTimeStamp,
		conversion: Conversion? = this.conversion,
		conversionId: String? = this.conversionId,
		landingPage: String? = this.landingPage,
		recirculationSource: String? = this.recirculationSource,
		cc: Int = this.cc,
		pm: Map<String, Int> = this.pageMetrics,
		cdpMasterId: String? = this.cdpMasterId,
		cdpRfv: String? = this.cdpRfv,
		cdpCohorts: String? = this.cdpCohorts
	) = IngestPingData(
		accountId,
		sessionTimeStamp,
		url,
		canonicalUrl,
		previousUrl,
		pageId,
		originalUserId,
		sessionId,
		pingCounter,
		currentTimeStamp,
		userType,
		registeredUserId,
		pageVars,
		sessionVars,
		userVars,
		userSegments,
		scrollPercent,
		firsVisitTimeStamp,
		previousSessionTimeStamp,
		timeOnPage,
		pageStartTimeStamp,
		conversion,
		conversionId,
		version,
		pageType,
		userConsent,
		landingPage,
		recirculationSource,
		cc,
		pm,
		cdpMasterId,
		cdpRfv,
		cdpCohorts
	)
}

internal class IngestPingDataSerializer : JsonSerializer<IngestPingData> {
	private val gson: Gson by lazy {
		GsonBuilder()
			.registerPingDataSerializer()
			.create()
	}

	override fun serialize(src: IngestPingData, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
		val pingData = gson.toJsonTree(src).asJsonObject

		src.conversion?.let { conversion ->
			pingData.addProperty("conv", conversion.name)
			conversion.options?.let { options ->
				options.initiator?.let { pingData.addProperty("conv_i", it) }
				options.value?.let { pingData.addProperty("cvv", it) }
				options.meta?.let { meta ->
					val metaArray = JsonArray()
					meta.forEach { (key, value) ->
						val pair = JsonArray()
						pair.add(key)
						pair.add(value)
						metaArray.add(pair)
					}
					pingData.add("cvar", metaArray)
				}
			}
		}

		return pingData
	}
}
