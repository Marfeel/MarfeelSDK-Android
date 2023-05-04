package com.marfeel.compass.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.RfvPayloadData
import com.marfeel.compass.core.model.multimedia.MultimediaPingData
import com.marfeel.compass.core.model.multimedia.MultimediaPingDataSerializer
import com.marfeel.compass.core.model.registerPingDataSerializer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException


private const val rfvPath = "rfv.php"

internal enum class PingPaths(val path: String) {
	INGEST("ingest.php"),
	MULTIMEDIA("multimedia.php")
}

internal enum class ContentType(val type: String) {
	JSON("application/json; charset=utf-8"),
	TEXT("text/plain"),
	FORM_DATA("application/x-www-form-urlencoded")
}

internal class ApiClient(
	private val httpClient: OkHttpClient,
	private val pingBaseUrl: String = BuildConfig.COMPASS_PING_BASE_URL,
	private val rfvBaseUrl: String = BuildConfig.COMPASS_RFV_BASE_URL
) {
	private val mediaType = ContentType.TEXT.type.toMediaType()
	private val gson:Gson by lazy {
		GsonBuilder()
			.registerTypeAdapter(MultimediaPingData::class.java, MultimediaPingDataSerializer())
			.registerPingDataSerializer()
			.create()
	}

	fun ping(path: PingPaths, pingData: PingData) {
		val contentType = if(path == PingPaths.INGEST) ContentType.FORM_DATA else ContentType.JSON

		val request = Request.Builder()
			.url("$pingBaseUrl/${path.path}")
			.ping(pingData, contentType)
			.build()

		try {
			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IOException("Unexpected code $response")

				println(response.body!!.string())
			}
		} catch (exception: IOException) {
			// TODO: track server errors, discarding connection errors
			println(exception.toString())
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
				return Result.success(gson.fromJson(rawResponse, RFV::class.java))
			}

			Result.success(null)
		} catch (exception: IOException) {
			Result.failure(exception)
		}
	}

	private fun Request.Builder.ping(ping: PingData, type: ContentType): Request.Builder {
		if (type == ContentType.FORM_DATA) {
			return this.pingFormData(ping)
		}

		return this.pingJson(ping)
	}

	private fun Request.Builder.pingJson(ping: PingData): Request.Builder {
		val data = gson.toJson(ping)

		return this.post(data.toRequestBody(ContentType.JSON.type.toMediaTypeOrNull()))
	}

	private fun Request.Builder.pingFormData(ping: PingData): Request.Builder {
		val data = gson.toJsonTree(ping).asJsonObject

		this.post(
			FormBody.Builder()
			.addJson(data)
			.build()
		)

		return this
	}
}
private fun FormBody.Builder.addJson(json: JsonObject): FormBody.Builder {
	for ((key, value) in json.entrySet()) {
		this.add(key, value.asString)
	}

	return this
}