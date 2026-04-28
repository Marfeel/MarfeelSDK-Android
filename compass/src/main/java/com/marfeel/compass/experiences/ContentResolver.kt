package com.marfeel.compass.experiences

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal class ContentResolver(private val httpClient: OkHttpClient) {
	private val gson = Gson()
	private val bundleMutex = Mutex()
	private val bundleStore = mutableMapOf<String, BundleEntry>()
	private val bundleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	suspend fun fetch(url: String, experienceId: String? = null): String? {
		if (experienceId != null && isBundledUrl(url)) {
			return fetchBundledSlice(url, experienceId)
		}
		return fetchRaw(url)
	}

	private suspend fun fetchRaw(url: String): String? = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder().url(url).build()
			httpClient.newCall(request).execute().use { response ->
				if (response.isSuccessful) response.body?.string() else null
			}
		} catch (_: Exception) {
			null
		}
	}

	private suspend fun fetchBundledSlice(url: String, experienceId: String): String? {
		val entry = bundleMutex.withLock {
			bundleStore.getOrPut(url) { BundleEntry() }
		}

		entry.loadMutex.withLock {
			if (entry.payload == null) {
				val body = fetchRaw(url) ?: return null
				entry.payload = parseBundle(body)
			}
		}

		extractSlice(entry, experienceId)?.let { return it }

		entry.loadMutex.withLock {
			if (!entry.varsReplayed && entry.payload?.vars?.isNotEmpty() == true) {
				entry.varsReplayed = true
				val replayUrl = resolveVarsFromUrl(url, entry.payload!!.vars)
				val body = fetchRaw(replayUrl) ?: return@withLock
				val retryPayload = parseBundle(body)
				entry.payload = mergeBundles(entry.payload!!, retryPayload)
			}
		}

		return extractSlice(entry, experienceId)
	}

	private fun extractSlice(entry: BundleEntry, experienceId: String): String? {
		val payload = entry.payload ?: return null
		synchronized(payload) {
			val list = payload.contents[experienceId] ?: return null
			if (list.isEmpty()) return null
			return list.removeAt(0)
		}
	}

	private fun parseBundle(body: String): BundledPayload {
		return try {
			val root = gson.fromJson(body, JsonObject::class.java) ?: JsonObject()
			val vars = mutableMapOf<String, String>()
			val contents = mutableMapOf<String, MutableList<String>>()
			for ((key, element) in root.entrySet()) {
				if (key == "vars" && element.isJsonObject) {
					for ((vKey, vValue) in element.asJsonObject.entrySet()) {
						vars[vKey] = if (vValue.isJsonPrimitive) vValue.asString else vValue.toString()
					}
					continue
				}
				if (element.isJsonArray) {
					contents[key] = extractArraySlices(element.asJsonArray)
				}
			}
			BundledPayload(contents, vars)
		} catch (_: Exception) {
			BundledPayload(mutableMapOf(), emptyMap())
		}
	}

	private fun extractArraySlices(array: JsonArray): MutableList<String> {
		val out = mutableListOf<String>()
		for (element in array) {
			out.add(element.toString())
		}
		return out
	}

	private fun mergeBundles(existing: BundledPayload, incoming: BundledPayload): BundledPayload {
		val mergedContents = mutableMapOf<String, MutableList<String>>()
		for ((key, list) in existing.contents) mergedContents[key] = list.toMutableList()
		for ((key, list) in incoming.contents) {
			val target = mergedContents.getOrPut(key) { mutableListOf() }
			for (item in list) if (item !in target) target.add(item)
		}
		val mergedVars = existing.vars + incoming.vars
		return BundledPayload(mergedContents, mergedVars)
	}

	internal data class BundledPayload(
		val contents: MutableMap<String, MutableList<String>>,
		val vars: Map<String, String>,
	)

	private class BundleEntry(
		val loadMutex: Mutex = Mutex(),
		var payload: BundledPayload? = null,
		var varsReplayed: Boolean = false,
	)

	companion object {
		private const val JUKEBOX_MARKER = "flowcards.mrf.io/transformer/"

		internal fun isBundledUrl(url: String): Boolean {
			val idParam = extractIdParam(url) ?: return false
			return idParam.contains(',')
		}

		private fun extractIdParam(url: String): String? {
			val httpUrl = url.toHttpUrlOrNull() ?: return null
			return httpUrl.queryParameter("id")
				?: httpUrl.queryParameter("url")?.toHttpUrlOrNull()?.queryParameter("id")
		}

		internal fun resolveVarsFromUrl(url: String, vars: Map<String, String>): String {
			if (vars.isEmpty()) return url
			val isJukebox = url.contains(JUKEBOX_MARKER)
			return if (isJukebox) applyVarsToJukeboxInnerUrl(url, vars) else applyVarsToUrl(url, vars)
		}

		private fun applyVarsToUrl(url: String, vars: Map<String, String>): String {
			val httpUrl = url.toHttpUrlOrNull() ?: return url
			return httpUrl.newBuilder().apply {
				for ((k, v) in vars) {
					removeAllQueryParameters(k)
					addQueryParameter(k, v)
				}
			}.build().toString()
		}

		private fun applyVarsToJukeboxInnerUrl(url: String, vars: Map<String, String>): String {
			val outer: HttpUrl = url.toHttpUrlOrNull() ?: return url
			val innerRaw = outer.queryParameter("url") ?: return url
			val rewrittenInner = applyVarsToUrl(innerRaw, vars)
			return outer.newBuilder().apply {
				removeAllQueryParameters("url")
				addQueryParameter("url", rewrittenInner)
			}.build().toString()
		}
	}
}
