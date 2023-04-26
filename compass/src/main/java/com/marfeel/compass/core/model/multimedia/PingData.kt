package com.marfeel.compass.core.model.multimedia

import com.google.gson.*
import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.core.model.compass.UserTypeSerializer
import com.marfeel.compass.tracker.multimedia.MultimediaItem
import java.lang.reflect.Type

internal class MultimediaPingData(
    accountId: String,
    currentTimeStamp: Long,
    sessionTimeStamp: Long,
    url: String,
    canonicalUrl: String,
    previousUrl: String,
    pageId: String,
    originalUserId: String,
    sessionId: String,
    userType: UserType,
    registeredUserId: String,
    firsVisitTimeStamp: Long,
    previousSessionTimeStamp: Long?,
    version: String,
    pingCounter: Int,
    @Transient
    val item: MultimediaItem,
    @Transient
    var rfv: RFV? = null
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

internal class MultimediaPingDataSerializer : JsonSerializer<MultimediaPingData> {
    private val gson:Gson by lazy {
        GsonBuilder().registerTypeAdapter(UserType::class.java, UserTypeSerializer()).create()
    }

    private fun <T>toMap(src: T): Map<*, *> = gson.fromJson(gson.toJson(src), Map::class.java)

    override fun serialize(src: MultimediaPingData, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        val pingData = toMap(src)
        val rfvData = toMap(src.rfv)
        val itemData = toMap(src.item)
        val itemMetadata = toMap(src.item.metadata)

        return gson.toJsonTree(pingData + rfvData + itemData + itemMetadata)
    }
}