package com.marfeel.compass.core.model.compass

import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import java.util.UUID
import kotlin.jvm.Transient

internal const val androidPageType = 4
internal const val androidPressReaderPageType = 11
internal val androidCorePageTypes = intArrayOf(androidPageType, androidPressReaderPageType)

/**
 *  Possible types of users.
 *
 * @property numericValue numeric representation of the user type. Values 1, 2, and 3 are reserved for the [Anonymous], [Logged], and [Paid] types.
 */
sealed class UserType(open val numericValue: Int) {
	object Anonymous : UserType(1)
	object Logged : UserType(2)
	object Paid : UserType(3)
	data class Custom(val customValue: Int) : UserType(customValue)
}

enum class ConversionScope {
	User,
	Session,
	Page
}

data class ConversionOptions(
	@SerializedName("conv_i")
	val initiator: String? = null,
	@SerializedName("cvid")
	val id: String? = null,
	@SerializedName("cvv")
	val value: String? = null,
	@SerializedName("cvar")
	val meta: Map<String, String>? = null,
	@Transient
	val scope: ConversionScope? = null
)

internal data class RfvPayloadData(
	@SerializedName("ac")
	val accountId: String,
	@SerializedName("sui")
	val registeredUserId: String?,
	@SerializedName("u")
	val originalUserId: String,
	@SerializedName("lv")
	val previousSessionTimeStamp: Long?
)

internal data class RFV(
	val rfv: Float,
	@SerializedName(value = "rfv_r", alternate = ["r"])
	val r: Int,
	@SerializedName(value = "rfv_f", alternate = ["f"])
	val f: Int,
	@SerializedName(value = "rfv_v", alternate = ["v"])
	val v: Int
) {
	constructor() : this(0f, 0, 0, 0)

	override fun toString(): String {
		val json = JSONObject()

		json.put("rfv", this.rfv)
		json.put("r", this.r)
		json.put("f", this.f)
		json.put("v", this.v)

		return json.toString()
	}
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

internal fun thirtyMinsAgoInSeconds() = (System.currentTimeMillis() / 1000) - (30 * 60)
