package com.marfeel.compass.cdp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO-8601 (de)serialization for meter `started_at` / `expires_at`. The SDK both
 * parses the backend's wire form and round-trips its own persisted form, so parsing
 * tolerates a few common shapes; serialization always emits UTC with milliseconds.
 */
private val isoFormats: List<SimpleDateFormat> by lazy {
	listOf(
		"yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
		"yyyy-MM-dd'T'HH:mm:ssXXX",
		"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
		"yyyy-MM-dd'T'HH:mm:ss'Z'"
	).map { pattern ->
		SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
	}
}

private val isoOut: SimpleDateFormat by lazy {
	SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
		timeZone = TimeZone.getTimeZone("UTC")
	}
}

internal fun parseIsoDate(value: String?): Date? {
	if (value.isNullOrEmpty()) return null
	for (format in isoFormats) {
		try {
			return format.parse(value)
		} catch (_: Exception) {
			// try the next pattern
		}
	}
	return null
}

internal fun formatIsoDate(date: Date?): String? =
	date?.let { isoOut.format(it) }
