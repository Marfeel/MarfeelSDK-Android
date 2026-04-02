package com.marfeel.compass.experiences

import okhttp3.OkHttpClient

class ContentResolver(private val httpClient: OkHttpClient) {
	suspend fun fetch(url: String): String? = null
}
