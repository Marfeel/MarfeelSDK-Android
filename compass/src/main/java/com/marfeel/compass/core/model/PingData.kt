package com.marfeel.compass.core.model

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.compass.UserType
import java.lang.reflect.Type

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
    @SerializedName("uvar")
    val userVars: Map<String, String>,
    @SerializedName("pvar")
    val pageVars: Map<String, String>,
    @SerializedName("svar")
    val sessionVars: Map<String, String>,
    @SerializedName("useg")
    val userSegments: List<String>,
    @SerializedName("pageType")
    val pageType: Int,
)

internal class UserTypeSerializer : JsonSerializer<UserType> {
    override fun serialize(src: UserType, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.numericValue.toString())
    }
}

internal class PingDataBooleanSerializer : JsonSerializer<Boolean> {
    override fun serialize(src: Boolean, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(if(src) 1 else 0)
    }
}

internal class PingDataVarsSerializer : JsonSerializer<Map<String, String>> {
    override fun serialize(src: Map<String, String>, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        val vars = src.toList()
        val res = JsonArray(vars.size)

        for(someVar in vars) {
            val serializedVar = JsonArray(2)

            serializedVar.add(someVar.first)
            serializedVar.add(someVar.second)
            res.add(serializedVar)
        }

        return res
    }
}

internal fun GsonBuilder.registerPingDataSerializer(): GsonBuilder {
    return this
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapter(UserType::class.java, UserTypeSerializer())
        .registerTypeAdapter(Boolean::class.javaObjectType, PingDataBooleanSerializer())
        .registerTypeAdapter(Map::class.java, PingDataVarsSerializer())
}