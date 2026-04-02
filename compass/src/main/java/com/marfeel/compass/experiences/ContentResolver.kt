package com.marfeel.compass.experiences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ContentResolver(private val httpClient: OkHttpClient) {
	suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder().url(url).build()
			val response = httpClient.newCall(request).execute()
			if (response.isSuccessful) response.body?.string() else null
		} catch (_: Exception) {
			null
		}
	}
}
