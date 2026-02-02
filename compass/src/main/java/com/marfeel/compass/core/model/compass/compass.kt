package com.marfeel.compass.core.model.compass

import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.PingData
import org.json.JSONObject
import java.util.UUID

internal const val androidPageType = 4
internal const val androidPressReaderPageType = 11
internal val androidCorePageTypes = intArrayOf(androidPageType, androidPressReaderPageType)

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
	@SerializedName("conv")
	val conversions: String?,
	@SerializedName("conv_i")
	val conversionInitiator: String? = null,
	@SerializedName("cvid")
	val conversionId: String? = null,
	@SerializedName("cvv")
	val conversionValue: String? = null,
	@SerializedName("cvar")
	val conversionMeta: List<List<String>>? = null,
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
	val pageMetrics: Map<String, Int>
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
		conversions: String? = this.conversions,
		conversionInitiator: String? = this.conversionInitiator,
		conversionId: String? = this.conversionId,
		conversionValue: String? = this.conversionValue,
		conversionMeta: List<List<String>>? = this.conversionMeta,
		landingPage: String? = this.landingPage,
		recirculationSource: String? = this.recirculationSource,
		cc: Int = this.cc,
		pm: Map<String, Int> = this.pageMetrics
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
		conversions,
		conversionInitiator,
		conversionId,
		conversionValue,
		conversionMeta,
		version,
		pageType,
		userConsent,
		landingPage,
		recirculationSource,
		cc,
		pm
	)
}

/**
 *  Possible types of users.
 *
 * @property numericValue numeric representation of the user type. Values 1, 2, and 3 are reserved for the [Anonymous], [Logged], and [Paid] types.
 */
sealed class UserType(open val numericValue: Int) {
	object Anonymous : UserType(1)
	object Logged : UserType(2)
	object Paid : UserType(3)
	data class Custom(val customValue: Int) : UserType(customValue)
}

enum class ConversionScope {
	User,
	Session,
	Page
}

data class ConversionOptions(
	val initiator: String? = null,
	val id: String? = null,
	val value: String? = null,
	val meta: Map<String, String>? = null,
	val scope: ConversionScope? = null
)

internal data class RfvPayloadData(
	@SerializedName("ac")
	val accountId: String,
	@SerializedName("sui")
	val registeredUserId: String?,
	@SerializedName("u")
	val originalUserId: String,
	@SerializedName("lv")
	val previousSessionTimeStamp: Long?
)

internal data class RFV(
	val rfv: Float,
	@SerializedName(value = "rfv_r", alternate = ["r"])
	val r: Int,
	@SerializedName(value = "rfv_f", alternate = ["f"])
	val f: Int,
	@SerializedName(value = "rfv_v", alternate = ["v"])
	val v: Int
) {
	constructor() : this(0f, 0, 0, 0)

	override fun toString(): String {
		val json = JSONObject()

		json.put("rfv", this.rfv)
		json.put("r", this.r)
		json.put("f", this.f)
		json.put("v", this.v)

		return json.toString()
	}
}

internal data class Session(
	val id: String,
	val timeStamp: Long
)

internal data class Page(
	val url: String,
	val pageId: String = UUID.randomUUID().toString(),
	val startTimeStamp: Long = currentTimeStampInSeconds()
)

internal fun currentTimeStampInSeconds() = System.currentTimeMillis() / 1000

internal fun thirtyMinsAgoInSeconds() = (System.currentTimeMillis() / 1000) - (30 * 60)
