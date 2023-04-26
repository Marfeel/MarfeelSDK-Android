package com.marfeel.compass.core.model

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.core.model.compass.androidPageType

internal open class PingData(
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
    @SerializedName("ut")
    val userType: UserType,
    @SerializedName("sui")
    val registeredUserId: String,
    @SerializedName("fv")
    val firsVisitTimeStamp: Long,
    @SerializedName("lv")
    val previousSessionTimeStamp: Long?,
    @SerializedName("v")
    val version: String,
    @SerializedName("n")
    val currentTimeStamp: Long,
    @SerializedName("a")
    val pingCounter: Int? = 0,
    @SerializedName("pageType")
    val pageType: Int = androidPageType
)