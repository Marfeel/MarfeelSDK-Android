package com.marfeel.compass.network

import android.util.Log
import com.google.gson.Gson
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.PingData
import com.marfeel.compass.core.RfvData
import com.marfeel.compass.core.androidPageType
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val pingPath = "ingest.php"
private const val rfvPath = "rfv.php"

internal class ApiClient(
	private val httpClient: OkHttpClient,
	private val pingBaseUrl: String = BuildConfig.COMPASS_PING_BASE_URL,
	private val rfvBaseUrl: String = BuildConfig.COMPASS_RFV_BASE_URL
) {
	private val mediaType = "application/json; charset=utf-8".toMediaType()

	fun ping(pingData: PingData) {
		val formBody = FormBody.Builder()
			.addPingRequest(pingData)
			.build()
		val request = Request.Builder()
			.url("$pingBaseUrl/$pingPath")
			.post(formBody)
			.build()

		val jsonRequest = Gson().toJson(pingData)
//		val request = Request.Builder()
//			.url("$pingBaseUrl/$pingPath")
//			.post(jsonRequest.toRequestBody(mediaType))
//			.build()
		httpClient.newCall(request).execute().use {
			if (it.isSuccessful) Log.d("Compass", "ping emmited")
		}
	}

	fun getRfv(rfvData: RfvData): Result<String?> {
		val jsonRequest = Gson().toJson(rfvData)
		val request = Request.Builder()
			.url("$rfvBaseUrl/$rfvPath")
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

private fun FormBody.Builder.addPingRequest(ping: PingData): FormBody.Builder =
	this.addEncoded("ac", ping.accountId)
		.addEncoded("t", ping.sessionTimeStamp.toString())
		.addEncoded("url", ping.url)
		.addEncoded("c", ping.url)
		.addEncoded("pp", ping.previousUrl)
		.addEncoded("p", ping.pageId)
		.addEncoded("u", ping.originalUserId)
		.addEncoded("s", ping.sessionId)
		.addEncoded("a", ping.pingCounter.toString())
		.addEncoded("n", ping.currentTimeStamp.toString())
		.addEncoded("ut", ping.userType.numericValue.toString())
		.addEncoded("sui", ping.registeredUserId)
		.addEncoded("sc", ping.scrollPercent.toString())
		.addEncoded("fv", ping.firsVisitTimeStamp.toString())
		.addEncoded("lv", ping.previousSessionTimeStamp?.toString() ?: "null")
		.addEncoded("l", ping.timeOnPage.toString())
		.addEncoded("ps", ping.pageStartTimeStamp.toString())
		.addEncoded("conv", ping.conversions ?: "")
		.addEncoded("v", BuildConfig.VERSION)
		.addEncoded("pageType", androidPageType.toString())
