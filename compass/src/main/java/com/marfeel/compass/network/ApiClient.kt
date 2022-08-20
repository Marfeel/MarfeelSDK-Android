package com.marfeel.compass.network

import com.marfeel.compass.core.PingRequest
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

private const val compassBaseURl = "https://compassdata.mrf.io"
private const val pingPath = "ingest.php"
private const val apiVersion = "0.2"
private const val pageType = "4"

internal class ApiClient(private val httpClient: OkHttpClient) {
    fun ping(pingRequest: PingRequest) {
        val formBody = FormBody.Builder()
            .addPingRequest(pingRequest)
            .build()
        val request = Request.Builder()
            .url("$compassBaseURl/$pingPath")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use {
            if (it.isSuccessful) Timber.d("ping emmited")
        }
    }

    fun getRfv() {

    }
}

private fun FormBody.Builder.addPingRequest(pingRequest: PingRequest): FormBody.Builder =
    this.addEncoded("ac",pingRequest.accountId)
        .addEncoded("t", pingRequest.sessionTimeStamp.toString())
        .addEncoded("r", pingRequest.referralUrl?:"")
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
        .addEncoded("lv", pingRequest.previousSessionTimeStamp?.toString()?:"null")
        .addEncoded("pageType", pageType)
