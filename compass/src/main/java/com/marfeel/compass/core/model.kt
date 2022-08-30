package com.marfeel.compass.core

import java.util.*

internal data class PingRequest(
	val accountId: String,
	val sessionTimeStamp: Long,
	val referralUrl: String?,
	val url: String,
	val previousUrl: String,
	val pageId: String,
	val userId: String,
	val sessionId: String,
	val pingCounter: Long,
	val currentTimeStamp: Long,
	val userType: UserType,
	val registeredUserId: String,
	val cookiesAllowed: Boolean,
	val scrollPercent: Int,
	val firsVisitTimeStamp: Long,
	val previousSessionTimeStamp: Long?,
	val timeOnPage: Long,
	val pageStartTimeStamp: Long,
	val conversions: String?
)

sealed class UserType(open val numericValue: Int) {
	object Anonymous : UserType(1)
	object Logged : UserType(2)
	object Paid : UserType(3)
	data class CustomUserJourney(override val numericValue: Int) : UserType(numericValue)
}

internal data class RfvRequest(
	val accountId: String,
	val userId: String,
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

fun currentTimeStampInSeconds() = System.currentTimeMillis() / 1000
