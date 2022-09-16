package com.marfeel.compass.network

import com.google.gson.Gson
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.PingData
import com.marfeel.compass.core.RfvData
import com.marfeel.compass.core.androidPageType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
		val boundary = "--401c9ccd-19db-4759-8da6-bd3a82e49867--"
		val formBody = MultipartBody.Builder(boundary)
			.setType(MultipartBody.FORM)
			.addPingRequest(pingData)
			.build()
		formBody.contentType()
		val request = Request.Builder()
			.addHeader("Content-Type", "multipart/form-data; boundary=$boundary")
			.url("$pingBaseUrl/$pingPath")
			.post(formBody)
			.build()

		httpClient.newCall(request).execute()
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

private fun MultipartBody.Builder.addPingRequest(ping: PingData): MultipartBody.Builder =
	this.addFormDataPart("ac", ping.accountId)
		.addFormDataPart("t", ping.sessionTimeStamp.toString())
		.addFormDataPart("url", ping.url)
		.addFormDataPart("c", ping.url)
		.addFormDataPart("pp", ping.previousUrl)
		.addFormDataPart("p", ping.pageId)
		.addFormDataPart("u", ping.originalUserId)
		.addFormDataPart("s", ping.sessionId)
		.addFormDataPart("a", ping.pingCounter.toString())
		.addFormDataPart("n", ping.currentTimeStamp.toString())
		.addFormDataPart("ut", ping.userType.numericValue.toString())
		.addFormDataPart("sui", ping.registeredUserId)
		.addFormDataPart("sc", ping.scrollPercent.toString())
		.addFormDataPart("fv", ping.firsVisitTimeStamp.toString())
		.addFormDataPart("lv", ping.previousSessionTimeStamp?.toString() ?: "null")
		.addFormDataPart("l", ping.timeOnPage.toString())
		.addFormDataPart("ps", ping.pageStartTimeStamp.toString())
		.addFormDataPart("conv", ping.conversions ?: "")
		.addFormDataPart("v", BuildConfig.VERSION)
		.addFormDataPart("pageType", androidPageType.toString())
