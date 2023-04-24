package com.marfeel.compass.core.model.compass

import com.marfeel.compass.core.model.PingData
import kotlinx.serialization.SerialName
import java.util.UUID

internal const val androidPageType = 4

internal data class IngestPingData(
	override val accountId: String,
	override val sessionTimeStamp: Long,
	override val url: String,
	override val canonicalUrl: String,
	override val previousUrl: String,
	override val pageId: String,
	override val originalUserId: String,
	override val sessionId: String,
	override val pingCounter: Int,
	override val currentTimeStamp: Long,
	override val userType: UserType,
	override val registeredUserId: String,
	@SerialName("sc")
	val scrollPercent: Int,
	override val firsVisitTimeStamp: Long,
	override val previousSessionTimeStamp: Long?,
	@SerialName("l")
	val timeOnPage: Int,
	@SerialName("ps")
	val pageStartTimeStamp: Long,
	@SerialName("conv")
	val conversions: String?,
	override val version: String,
	@SerialName("pageType")
	val pageType: Int = androidPageType
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
	pingCounter
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
	@SerialName("ac")
	val accountId: String,
	@SerialName("sui")
	val registeredUserId: String?,
	@SerialName("u")
	val originalUserId: String,
	@SerialName("lv")
	val previousSessionTimeStamp: Long?
)

internal data class RFV(
	val rfv: Float,
	val r: Float,
	val f: Float,
	val v: Float
) {
	constructor() : this(0f, 0f, 0f, 0f)
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
