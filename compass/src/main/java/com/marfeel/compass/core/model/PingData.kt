package com.marfeel.compass.core.model

import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.compass.UserType

internal open class PingData(
    @SerializedName("ac")
    open val accountId: String,
    @SerializedName("t") 
    open val sessionTimeStamp: Long,
    @SerializedName("url")
    open val url: String,
    @SerializedName("c")
    open val canonicalUrl: String,
    @SerializedName("pp")
    open val previousUrl: String,
    @SerializedName("p")
    open val pageId: String,
    @SerializedName("u")
    open val originalUserId: String,
    @SerializedName("s")
    open val sessionId: String,
    @SerializedName("ut")
    open val userType: UserType,
    @SerializedName("sui")
    open val registeredUserId: String,
    @SerializedName("fv")
    open val firsVisitTimeStamp: Long,
    @SerializedName("lv")
    open val previousSessionTimeStamp: Long?,
    @SerializedName("v")
    open val version: String,
    @SerializedName("n")
    open val currentTimeStamp: Long,
    @SerializedName("a")
    open val pingCounter: Int? = 0
)
