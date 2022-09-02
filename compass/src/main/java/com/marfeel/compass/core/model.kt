package com.marfeel.compass.core

import com.google.gson.annotations.SerializedName
import java.util.UUID

private const val androidPageType = 4

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
	@SerializedName("v")
	val version: String,
	@SerializedName("pageType")
	val pageType: Int = androidPageType
)

sealed class UserType(open val numericValue: Int) {
	object Anonymous : UserType(1)
	object Logged : UserType(2)
	object Paid : UserType(3)
	data class CustomUserJourney(override val numericValue: Int) : UserType(numericValue)
}

internal data class RfvData(
	@SerializedName("ac")
	val accountId: String,
	@SerializedName("sui")
	val registeredUserId: String?,
	@SerializedName("u")
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
