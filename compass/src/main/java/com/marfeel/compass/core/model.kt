package com.marfeel.compass.core

import java.util.*

internal data class PingData(
	val accountId: String,
	val sessionTimeStamp: Long,
	val url: String,
	val previousUrl: String,
	val pageId: String,
	val originalUserId: String,
	val sessionId: String,
	val pingCounter: Int,
	val currentTimeStamp: Long,
	val userType: UserType,
	val registeredUserId: String,
	val scrollPercent: Int,
	val firsVisitTimeStamp: Long,
	val previousSessionTimeStamp: Long?,
	val timeOnPage: Int,
	val pageStartTimeStamp: Long,
	val conversions: String?
)

sealed class UserType(open val numericValue: Int) {
	object Anonymous : UserType(1)
	object Logged : UserType(2)
	object Paid : UserType(3)
	data class CustomUserJourney(override val numericValue: Int) : UserType(numericValue)
}

internal data class RfvData(
	val accountId: String,
	val registeredUserId: String?,
	val originalUserId: String
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
