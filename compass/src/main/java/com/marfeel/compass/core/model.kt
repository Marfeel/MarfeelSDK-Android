package com.marfeel.compass.core

import com.google.gson.annotations.SerializedName
import java.util.UUID

internal const val androidPageType = 4

internal data class PingData(
	@SerializedName("ac")
	val accountId: String,
	@SerializedName("t")
	val sessionTimeStamp: Long,
	@SerializedName("url")
	val url: String,
	@SerializedName("c")
	val canonicalUrl: String,
	@SerializedName("pp")
	val previousUrl: String,
	@SerializedName("p")
	val pageId: String,
	@SerializedName("u")
	val originalUserId: String,
	@SerializedName("s")
	val sessionId: String,
	@SerializedName("a")
	val pingCounter: Int,
	@SerializedName("n")
	val currentTimeStamp: Long,
	@SerializedName("ut")
	val userType: UserType,
	@SerializedName("sui")
	val registeredUserId: String,
	@SerializedName("sc")
	val scrollPercent: Int,
	@SerializedName("fv")
	val firsVisitTimeStamp: Long,
	@SerializedName("lv")
	val previousSessionTimeStamp: Long?,
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
	@SerializedName("v")
	val version: String,
	@SerializedName("pageType")
	val pageType: Int = androidPageType
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

/**
 * Scope for a conversion event.
 */
enum class ConversionScope {
	User,
	Session,
	Page
}

/**
 * Options for tracking a conversion event.
 *
 * @property initiator The initiator of the conversion.
 * @property id The identifier of the conversion.
 * @property value The value associated with the conversion.
 * @property meta Additional metadata as key-value pairs.
 * @property scope The scope of the conversion.
 */
data class ConversionOptions(
	val initiator: String? = null,
	val id: String? = null,
	val value: String? = null,
	val meta: Map<String, String>? = null,
	val scope: ConversionScope? = null
)

internal data class RfvData(
	@SerializedName("ac")
	val accountId: String,
	@SerializedName("sui")
	val registeredUserId: String?,
	@SerializedName("u")
	val originalUserId: String,
	@SerializedName("lv")
	val previousSessionTimeStamp: Long?
)

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
