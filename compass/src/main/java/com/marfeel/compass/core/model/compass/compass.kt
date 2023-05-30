package com.marfeel.compass.core.model.compass

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.PingData
import org.json.JSONObject
import java.util.UUID

internal const val androidPageType = 4

internal class IngestPingData(
	accountId: String,
	sessionTimeStamp: Long,
	url: String,
	canonicalUrl: String,
	previousUrl: String,
	pageId: String,
	originalUserId: String,
	sessionId: String,
	pingCounter: Int,
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
	version: String
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
	userSegments
)

/**
 *  Possible types of users.
 *
 * @property numericValue numeric representation of the user type. Values 1, 2, and 3 are reserved for the [Anonymous], [Logged], and [Paid] types.
 */
sealed class UserType(open val numericValue: Int) {
	object Anonymous : UserType(1)
	object Logged : UserType(2)
	object Paid : UserType(3)
	data class Custom(override val numericValue: Int) : UserType(numericValue)
}

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
