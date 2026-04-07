package com.marfeel.compass.experiences

import com.google.gson.Gson
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

internal class RecirculationApiClient(
	private val httpClient: OkHttpClient,
	private val storage: Storage,
	private val sessionStorage: SessionStorage,
	private val pingBaseUrl: String = BuildConfig.COMPASS_PING_BASE_URL
) {
	private val gson = Gson()

	fun send(eventType: String, experiences: List<Experience>) {
		if (experiences.isEmpty()) return

		val modules = experiences.map { experience ->
			mapOf(
				"n" to experience.id,
				"e" to listOf(
					mapOf(
						"url" to (experience.contentUrl ?: ""),
						"p" to "255"
					)
				)
			)
		}

		val formBody = FormBody.Builder()
			.add("t", eventType)
			.add("n", currentTimeStampInSeconds().toString())
			.add("m", gson.toJson(modules))
			.add("ac", sessionStorage.readAccountId() ?: "")
			.add("url", sessionStorage.readPage()?.url ?: "")
			.add("ut", storage.readUserType().numericValue.toString())
			.add("fv", storage.readFirstSessionTimeStamp().toString())
			.add("u", storage.readOriginalUserId())
			.add("s", sessionStorage.readSession().id)
			.add("pageType", (sessionStorage.readPageTechnology() ?: 4).toString())

		storage.readRegisteredUserId()?.let { formBody.add("sui", it) }

		val consent = storage.readUserConsent()
		formBody.add("uc", (consent ?: "").toString())
		formBody.add("cc", when (consent) {
			true -> "1"
			false -> "0"
			null -> "3"
		})

		sessionStorage.readLandingPage()?.let { formBody.add("lp", it) }

		val request = Request.Builder()
			.url("$pingBaseUrl/recirculation/recirculation.php")
			.post(formBody.build())
			.build()

		try {
			httpClient.newCall(request).execute().close()
		} catch (_: Exception) {
			// Fire-and-forget — silently ignore failures
		}
	}
}
