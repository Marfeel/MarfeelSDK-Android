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
private const val pageType = 4

internal class ApiClient(
	private val httpClient: OkHttpClient,
	private val baseUrl: String = BuildConfig.COMPASS_BASE_URL
) {
	private val mediaType = "application/json; charset=utf-8".toMediaType()

	fun ping(pingData: PingData) {
		val jsonRequest = Gson().toJson(pingData.toPingRequest())
		val request = Request.Builder()
			.url("$baseUrl/$pingPath")
			.post(jsonRequest.toRequestBody(mediaType))
			.build()
		httpClient.newCall(request).execute().use {
			if (it.isSuccessful) Log.d("Compass", "ping emmited")
		}
	}

	fun getRfv(rfvData: RfvData): Result<String?> {
		val jsonRequest = Gson().toJson(rfvData.toRfvRequest())
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

private fun PingData.toPingRequest() =
	PingRequest(
		ac = accountId,
		t = sessionTimeStamp,
		url = url,
		c = url,
		pp = previousUrl,
		p = pageId,
		u = originalUserId,
		s = sessionId,
		a = pingCounter,
		n = currentTimeStamp,
		ut = userType.numericValue,
		sui = registeredUserId,
		sc = scrollPercent,
		fv = firsVisitTimeStamp,
		lv = previousSessionTimeStamp,
		l = timeOnPage,
		ps = pageStartTimeStamp,
		conv = conversions,
		pageType = pageType,
		v = "" //TODO: Cannot do it using BuildConfig.VERSION_NAME
)

private fun RfvData.toRfvRequest() =
	RfvRequest(
		ac = accountId,
		u = originalUserId,
		sui = registeredUserId
	)
