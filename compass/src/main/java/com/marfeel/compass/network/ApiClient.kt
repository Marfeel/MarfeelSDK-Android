package com.marfeel.compass.network

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.RfvPayloadData
import com.marfeel.compass.core.model.compass.androidPageType
import com.marfeel.compass.core.model.multimedia.MultimediaPingData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
		val formBody = FormBody.Builder()
			.addPingRequest(pingData)
			.build()

		val request = Request.Builder()
			.url("$pingBaseUrl/${path.path}")
			.post(formBody)
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

private fun FormBody.Builder.addPingRequest(ping: PingData): FormBody.Builder {
	this.add("ac", ping.accountId)
		.add("t", ping.sessionTimeStamp.toString())
		.add("url", ping.url)
		.add("c", ping.url)
		.add("pp", ping.previousUrl)
		.add("p", ping.pageId)
		.add("u", ping.originalUserId)
		.add("s", ping.sessionId)
		.add("n", ping.currentTimeStamp.toString())
		.add("ut", ping.userType.numericValue.toString())
		.add("sui", ping.registeredUserId)
		.add("fv", ping.firsVisitTimeStamp.toString())
		.add("lv", ping.previousSessionTimeStamp?.toString() ?: "null")
		.add("v", BuildConfig.VERSION)
		.add("pageType", androidPageType.toString())
		.add("a", ping.pingCounter.toString())
	if (ping is IngestPingData) {
		return this.addPingRequest(ping)
	} else if (ping is MultimediaPingData) {
		return this.addPingRequest(ping)
	}

	return this;
}

private fun FormBody.Builder.addPingRequest(ping: IngestPingData): FormBody.Builder =
	this.add("sc", ping.scrollPercent.toString())
		.add("l", ping.timeOnPage.toString())
		.add("ps", ping.pageStartTimeStamp.toString())
		.add("conv", ping.conversions ?: "")

private fun FormBody.Builder.addPingRequest(ping: MultimediaPingData): FormBody.Builder {
	val format = Json { encodeDefaults = true }

	this.add("ac", ping.accountId)
		.add("n", ping.currentTimeStamp.toString())
		.add("v", BuildConfig.VERSION)
		.add("pageType", androidPageType.toString())
		.add("imp", ping.item.imp)
		.add("m_p", ping.item.provider)
		.add("m_pi", ping.item.providerId)
		.add("m_t", ping.item.type.id)
		.add("m", format.encodeToString(ping.item.playbackInfo))

	ping.rfv?.let {
		this.add("rfv", it.rfv.toString())
			.add("rfv_r", it.r.toString())
			.add("rfv_f", it.f.toString())
			.add("rfv_v", it.v.toString())
	}

	return this
}