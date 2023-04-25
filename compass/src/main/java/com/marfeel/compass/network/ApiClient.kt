package com.marfeel.compass.network

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.RfvPayloadData
import com.marfeel.compass.core.model.compass.androidPageType
import com.marfeel.compass.core.model.multimedia.MultimediaPingData
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException


private const val rfvPath = "rfv.php"

internal enum class PingPaths(val path: String) {
	INGEST("ingest.php"),
	MULTIMEDIA("multimedia.php")
}

internal class ApiClient(
	private val httpClient: OkHttpClient,
	private val pingBaseUrl: String = BuildConfig.COMPASS_PING_BASE_URL,
	private val rfvBaseUrl: String = BuildConfig.COMPASS_RFV_BASE_URL
) {
	private val mediaType = "text/plain".toMediaType()

	fun ping(path: PingPaths, pingData: PingData) {
		val request = Request.Builder()
			.url("$pingBaseUrl/${path.path}")
			.ping(pingData)
			.build()

		try {
			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IOException("Unexpected code $response")

				println(response.body!!.string())
			}
		} catch (exception: IOException) {
			// TODO: track server errors, discarding connection errors
			println(exception.toString());
		}
	}

	fun ingestPing(pingData: IngestPingData) {
		ping(PingPaths.INGEST, pingData)
	}

	fun multimediaPing(pingData: MultimediaPingData) {
		ping(PingPaths.MULTIMEDIA, pingData)
	}

	fun getRfv(rfvPayloadData: RfvPayloadData): Result<RFV?> {
		val jsonRequest = Gson().toJson(rfvPayloadData)

		val request = Request.Builder()
			.url("$rfvBaseUrl/$rfvPath")
			.post(jsonRequest.toRequestBody(mediaType))
			.build()
		return try {
			val response = httpClient.newCall(request).execute()
			val rawResponse = response.body?.string()

			rawResponse?.let {
				val objectMapper = ObjectMapper()

				return Result.success(objectMapper.readValue(it, RFV::class.java))
			}

			Result.success(null)
		} catch (exception: IOException) {
			Result.failure(exception)
		}
	}
}

private fun getData(ping: PingData): Map<String, Any?> {
	if (ping is IngestPingData) {
		return getData(ping)
	} else if (ping is MultimediaPingData) {
		return getData(ping)
	}

	return getCommonData(ping)
}

private fun getData(ping: IngestPingData): Map<String, Any?> {
	return mapOf(
		"sc" to ping.scrollPercent.toString(),
		"l" to ping.timeOnPage.toString(),
		"ps" to ping.pageStartTimeStamp.toString(),
		"conv" to (ping.conversions ?: "")
	) + getCommonData(ping)
}

private fun getData(ping: MultimediaPingData): Map<String, Any?> {
	val gsonMapper = GsonBuilder().serializeNulls().create()
	val format = Json { encodeDefaults = true }
	/*val rfv = if(ping.rfv != null) format.encodeToJsonElement(ping.rfv).jsonObject.toPrimitivesMap() else mapOf()
	val item = format.encodeToJsonElement(ping.item).jsonObject.toPrimitivesMap()*/
	val rfv = if(ping.rfv != null) gsonMapper.fromJson(gsonMapper.toJson(ping.rfv), Map::class.java) as Map<String, Any> else mapOf()
	val item = gsonMapper.fromJson(gsonMapper.toJson(ping.item), Map::class.java) as Map<String, Any>
	/*return mapOf(
		"n" to ping.currentTimeStamp.toString(),
		"pageType" to androidPageType.toString(),
		"imp" to ping.item.imp,
		"m_p" to ping.item.provider,
		"m_pi" to ping.item.providerId,
		"m_t" to ping.item.type.id,
		"m" to format.encodeToJsonElement(ping.item.playbackInfo).jsonObject,
		"m_il" to ping.item.metadata.isLive.toString(),
		"m_ti" to (ping.item.metadata.title ?: ""),
		"m_d" to (ping.item.metadata.description ?: ""),
		"m_u" to (ping.item.metadata.url ?: ""),
		"m_th" to (ping.item.metadata.thumbnail ?: ""),
		"m_a" to (ping.item.metadata.authors ?: ""),
		"m_pt" to ping.item.metadata.publishTime.toString(),
		"m_l" to ping.item.metadata.duration.toString()
	) + getCommonData(ping) + rfv*/

	return getCommonData(ping) + rfv + item
}

private fun getCommonData(ping: PingData): Map<String, Any> {
	return mapOf(
		"ac" to ping.accountId,
		"t" to ping.sessionTimeStamp.toString(),
		"url" to ping.url,
		"c" to ping.url,
		"pp" to ping.previousUrl,
		"p" to ping.pageId,
		"u" to ping.originalUserId,
		"s" to ping.sessionId,
		"n" to ping.currentTimeStamp.toString(),
		"ut" to ping.userType.numericValue.toString(),
		"sui" to ping.registeredUserId,
		"fv" to ping.firsVisitTimeStamp.toString(),
		"lv" to (ping.previousSessionTimeStamp?.toString() ?: ""),
		"v" to BuildConfig.VERSION,
		"pageType" to androidPageType.toString(),
		"a" to ping.pingCounter.toString()
	)
}

private fun FormBody.Builder.addPingRequest(ping: PingData): FormBody.Builder {
	val data = getData(ping)

	for (key in data.keys) {
		val stringValue = if (data[key] is String) data[key] as String else data[key].toString()

		this.add(key, stringValue)
	}

	return this
}

private fun Request.Builder.ping(ping: PingData): Request.Builder {
	if (ping is IngestPingData) {
		return this.ping(ping)
	} else if (ping is MultimediaPingData) {
		return this.ping(ping)
	}

	return this;
}

private fun Request.Builder.ping(ping: IngestPingData): Request.Builder {
	val data = FormBody.Builder()
		.addPingRequest(ping)
		.build()

	return this.post(data)
}

private fun Request.Builder.ping(ping: MultimediaPingData): Request.Builder {
	val data = JSONObject(getData(ping)).toString()

	return this.post(data.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
}

private inline fun JsonObject.toPrimitivesMap(): Map<String, Any?> {
	val format = Json { encodeDefaults = true }

	return jsonObjectToMap(format.encodeToJsonElement(this).jsonObject)
}
private fun jsonObjectToMap(element: JsonObject): Map<String, Any?> {
	return element.entries.associate {
		it.key to extractValue(it.value)
	}
}

private fun extractValue(element: JsonElement): Any? {
	return when (element) {
		is JsonNull -> null
		is JsonPrimitive -> element.content
		is JsonArray -> element.map { extractValue(it) }
		is JsonObject -> jsonObjectToMap(element)
	}
}