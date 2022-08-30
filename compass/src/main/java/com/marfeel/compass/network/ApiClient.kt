package com.marfeel.compass.network

import android.util.Log
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.PingRequest
import com.marfeel.compass.core.RfvRequest
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val pingPath = "ingest.php"
private const val rfvPath = "data.php"
private const val apiVersion = "0.2"
private const val pageType = "4"

internal class ApiClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = BuildConfig.COMPASS_BASE_URL
) {
    fun ping(pingRequest: PingRequest) {
        val formBody = FormBody.Builder()
            .addPingRequest(pingRequest)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/$pingPath")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use {
            if (it.isSuccessful) Log.d("Compass","ping emmited")
        }
    }

    fun getRfv(rfvRequest: RfvRequest): Result<String?> {
        val formBody = FormBody.Builder()
            .addRfvRequest(rfvRequest)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/$rfvPath")
            .post(formBody)
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            Result.success(response.body?.string())
        } catch (exception: IOException) {
            Result.failure(exception)
        }
    }
}

private fun FormBody.Builder.addPingRequest(pingRequest: PingRequest): FormBody.Builder =
    this.addEncoded("ac", pingRequest.accountId)
        .addEncoded("t", pingRequest.sessionTimeStamp.toString())
        .addEncoded("r", pingRequest.referralUrl ?: "")
        .addEncoded("url", pingRequest.url)
        .addEncoded("c", pingRequest.url)
        .addEncoded("pp", pingRequest.previousUrl)
        .addEncoded("p", pingRequest.pageId)
        .addEncoded("u", pingRequest.userId)
        .addEncoded("s", pingRequest.sessionId)
        .addEncoded("v", apiVersion)
        .addEncoded("a", pingRequest.pingCounter.toString())
        .addEncoded("n", pingRequest.currentTimeStamp.toString())
        .addEncoded("l", pingRequest.timeOnPage.toString())
        .addEncoded("ps", pingRequest.pageStartTimeStamp.toString())
        .addEncoded("ut", pingRequest.userType.numericValue.toString())
        .addEncoded("uc", pingRequest.cookiesAllowed.toString())
        .addEncoded("sc", pingRequest.scrollPercent.toString())
        .addEncoded("fv", pingRequest.firsVisitTimeStamp.toString())
        .addEncoded("lv", pingRequest.previousSessionTimeStamp?.toString() ?: "null")
        .addEncoded("pageType", pageType)

private fun FormBody.Builder.addRfvRequest(request: RfvRequest): FormBody.Builder =
    this.addEncoded("ac", request.accountId)
        .addEncoded("u", request.userId)

