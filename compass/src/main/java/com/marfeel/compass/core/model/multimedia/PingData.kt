package com.marfeel.compass.core.model.multimedia

import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.tracker.multimedia.MultimediaItem

internal data class MultimediaPingData(
    override val accountId: String,
    override val currentTimeStamp: Long,
    override val sessionTimeStamp: Long,
    override val url: String,
    override val canonicalUrl: String,
    override val previousUrl: String,
    override val pageId: String,
    override val originalUserId: String,
    override val sessionId: String,
    override val userType: UserType,
    override val registeredUserId: String,
    override val firsVisitTimeStamp: Long,
    override val previousSessionTimeStamp: Long?,
    override val version: String,
    override val pingCounter: Int,
    val item: MultimediaItem,
    val rfv: RFV? = null
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