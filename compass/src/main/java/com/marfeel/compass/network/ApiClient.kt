package com.marfeel.compass.network

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
	private val mediaType = "text/plain".toMediaType()

	fun ping(pingData: PingData) {
		val formBody = FormBody.Builder()
			.addPingRequest(pingData)
			.build()

		val request = Request.Builder()
			.url("$pingBaseUrl/$pingPath")
			.post(formBody)
			.build()

		httpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) throw IOException("Unexpected code $response")

			println(response.body!!.string())
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
	this.add("ac", ping.accountId)
		.add("t", ping.sessionTimeStamp.toString())
		.add("url", ping.url)
		.add("c", ping.url)
		.add("pp", ping.previousUrl)
		.add("p", ping.pageId)
		.add("u", ping.originalUserId)
		.add("s", ping.sessionId)
		.add("a", ping.pingCounter.toString())
		.add("n", ping.currentTimeStamp.toString())
		.add("ut", ping.userType.numericValue.toString())
		.add("sui", ping.registeredUserId)
		.add("sc", ping.scrollPercent.toString())
		.add("fv", ping.firsVisitTimeStamp.toString())
		.add("lv", ping.previousSessionTimeStamp?.toString() ?: "null")
		.add("l", ping.timeOnPage.toString())
		.add("ps", ping.pageStartTimeStamp.toString())
		.add("conv", ping.conversions ?: "")
		.add("v", BuildConfig.VERSION)
		.add("pageType", androidPageType.toString())
