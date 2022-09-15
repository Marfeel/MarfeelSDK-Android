package com.marfeel.compass.network

import android.util.Log
import com.google.gson.Gson
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.PingData
import com.marfeel.compass.core.RfvData
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val pingPath = "ingest.php"
private const val rfvPath = "/rfv.php"

internal class ApiClient(
    private val httpClient: OkHttpClient,
    private val pingBaseUrl: String = BuildConfig.COMPASS_PING_BASE_URL,
    private val rfvBaseUrl: String = BuildConfig.COMPASS_RFV_BASE_URL
) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun ping(pingData: PingData) {
        val jsonRequest = Gson().toJson(pingData)

        val request = Request.Builder()
            .url("$pingBaseUrl/$pingPath")
            .post(pingData.toFormBody())
            .build()
        httpClient.newCall(request).execute().use {
            if (it.isSuccessful) Log.d("Compass", "ping emmited")
        }
    }

    private fun PingData.toFormBody() = FormBody.Builder()
        .add("ac", accountId)
        .add("t", sessionTimeStamp.toString())
        .add("url", url)
        .add("c", canonicalUrl)
        .add("pp", previousUrl)
        .add("p", pageId)
        .add("u", originalUserId)
        .add("s", sessionId)
        .add("a", pingCounter.toString())
        .add("n", currentTimeStamp.toString())
        .add("ut", userType.numericValue.toString())
        .add("sui", registeredUserId)
        .add("sc", scrollPercent.toString())
        .add("fv", firsVisitTimeStamp.toString())
        .add("lv", previousSessionTimeStamp.toString())
        .add("l", timeOnPage.toString())
        .add("ps", pageStartTimeStamp.toString())
        .add("conv", conversions.toString())
        .add("v", version)
        .add("pageType", firsVisitTimeStamp.toString())
        .add("fv", pageType.toString())
        .build()

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
