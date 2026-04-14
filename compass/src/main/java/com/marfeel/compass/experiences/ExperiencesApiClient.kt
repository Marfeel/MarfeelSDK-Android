package com.marfeel.compass.experiences

import com.marfeel.compass.BuildConfig
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal class ExperiencesApiClient(
	private val httpClient: OkHttpClient,
	private val storage: Storage,
	private val sessionStorage: SessionStorage,
	private val experimentManager: ExperimentManager,
	private val frequencyCapManager: FrequencyCapManager,
	private val readEditorialsManager: ReadEditorialsManager,
	baseUrl: String = BuildConfig.EXPERIENCES_BASE_URL,
	private val networkInfoProvider: NetworkInfoProvider? = null
) {
	private val baseUrl: String = baseUrl.trimEnd('/')
	fun fetch(url: String, customTargeting: Map<String, String>): String? {
		val httpUrl = buildUrl(url, customTargeting) ?: return null
		val request = Request.Builder().url(httpUrl).build()

		return try {
			httpClient.newCall(request).execute().use { response ->
				if (response.isSuccessful) response.body?.string() else null
			}
		} catch (_: Exception) {
			null
		}
	}

	private fun buildUrl(pageUrl: String, customTargeting: Map<String, String>): HttpUrl? {
		val builder = "$baseUrl/json/experiences".toHttpUrlOrNull()?.newBuilder() ?: return null

		builder.addQueryParameter("sid", sessionStorage.readAccountId() ?: "")
		builder.addQueryParameter("ptch", (sessionStorage.readPageTechnology() ?: 4).toString())
		builder.addQueryParameter("url", pageUrl)
		builder.addQueryParameter("canonical_url", pageUrl)
		builder.addQueryParameter("seid", sessionStorage.readSession().id)
		builder.addQueryParameter("uid", storage.readOriginalUserId())

		storage.readRegisteredUserId()?.let { builder.addQueryParameter("suid", it) }

		builder.addQueryParameter("utyp", storage.readUserType().numericValue.toString())
		builder.addQueryParameter("fvst", storage.readFirstSessionTimeStamp().toString())

		val segments = storage.readUserSegments()
		if (segments.isNotEmpty()) {
			builder.addQueryParameter("useg", segments.joinToString(","))
		}

		val conversions = sessionStorage.readPendingConversions()
		if (conversions.isNotEmpty()) {
			builder.addQueryParameter("cnv", conversions.joinToString(",") { it.name })
		}

		sessionStorage.readPreviousUrl()?.let { builder.addQueryParameter("ref", it) }

		networkInfoProvider?.getConnectionSpeedKbps()?.let {
			builder.addQueryParameter("kbps", it.toString())
		}
		networkInfoProvider?.getConnectionType()?.let {
			builder.addQueryParameter("ctyp", it)
		}

		val uexp = frequencyCapManager.buildUexp()
		if (uexp.isNotEmpty()) {
			builder.addQueryParameter("uexp", uexp)
		}

		val red = readEditorialsManager.buildRedParam()
		if (red.isNotEmpty()) {
			builder.addQueryParameter("red", red)
		}

		builder.addQueryParameter("v", "2")

		val trg = buildTargetingParam(customTargeting)
		if (trg.isNotEmpty()) {
			builder.addQueryParameter("trg", trg)
		}

		return builder.build()
	}

	private fun buildTargetingParam(customTargeting: Map<String, String>): String {
		val parts = mutableListOf<String>()

		for ((key, value) in storage.readUserVars()) {
			parts.add("userVar::$key=$value")
		}
		for ((key, value) in sessionStorage.readSessionVars()) {
			parts.add("sessionVar::$key=$value")
		}
		for ((key, value) in sessionStorage.readPageVars()) {
			parts.add("pageVar::$key=$value")
		}
		for ((key, value) in experimentManager.getTargetingEntries()) {
			parts.add("$key=$value")
		}
		for ((key, value) in customTargeting) {
			parts.add("$key=$value")
		}

		return parts.joinToString("&")
	}
}
