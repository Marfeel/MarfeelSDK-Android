package com.marfeel.compass.network

import android.util.Log
import com.google.gson.Gson
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.PingData
import com.marfeel.compass.core.RfvData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val pingPath = "ingest.php"
private const val rfvPath = "data.php"

internal class ApiClient(
	private val httpClient: OkHttpClient,
	private val baseUrl: String = BuildConfig.COMPASS_BASE_URL
) {
	private val mediaType = "application/json; charset=utf-8".toMediaType()

	fun ping(pingData: PingData) {
		val jsonRequest = Gson().toJson(pingData)
		val request = Request.Builder()
			.url("$baseUrl/$pingPath")
			.post(jsonRequest.toRequestBody(mediaType))
			.build()
		httpClient.newCall(request).execute().use {
			if (it.isSuccessful) Log.d("Compass", "ping emmited")
		}
	}

	fun getRfv(rfvData: RfvData): Result<String?> {
		val jsonRequest = Gson().toJson(rfvData)
		val request = Request.Builder()
			.url("$baseUrl/$rfvPath")
			.post(jsonRequest.toRequestBody(mediaType))
			.build()
		return try {
			val response = httpClient.newCall(request).execute()
			Result.success(response.body?.string())
		} catch (exception: IOException) {
			Result.failure(exception)
		}
	}
}
