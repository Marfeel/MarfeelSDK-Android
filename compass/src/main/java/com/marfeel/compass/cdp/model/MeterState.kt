package com.marfeel.compass.cdp.model

import java.util.Date

/**
 * The metering window the backend owns (calendar month, rolling 7 days, ...).
 * A missing window normalizes to empty strings.
 */
data class MeterWindow(
	val duration: String = "",
	val period: String = "",
	val tz: String = ""
)

/**
 * Server-authoritative meter counter (e.g. metered paywall). The backend owns the
 * count, window, threshold and expiry; the SDK reads and increments.
 *
 * The [threshold]/[reached]/[remaining] trio is present **only** when the meter has a
 * threshold configured — preserve "absent vs present", don't default [threshold] to 0.
 */
data class MeterState(
	val name: String,
	val count: Int = 0,
	val threshold: Int? = null,
	val reached: Boolean? = null,
	val remaining: Int? = null,
	val startedAt: Date? = null,
	val expiresAt: Date? = null,
	val window: MeterWindow = MeterWindow()
)

/**
 * Thrown by `incrementMeter` on a 404 — the meter is not configured for this site.
 * Distinct from a generic failure (which fails open to the mirror value).
 */
class MeterNotFoundError(val meterName: String) :
	Exception("meter_not_found: $meterName")
