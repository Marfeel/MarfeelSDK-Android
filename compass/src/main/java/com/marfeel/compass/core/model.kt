package com.marfeel.compass.core

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
    val pageStartTimeStamp:Long
) {
}

internal enum class UserType(val numericValue: Int){

}

