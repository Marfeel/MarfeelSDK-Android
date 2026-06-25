package com.marfeel.compass.cdp.model

import java.util.Date

data class MeterWindow(
	val duration: String = "",
	val period: String = "",
	val tz: String = ""
)

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

class MeterNotFoundError(val meterName: String) :
	Exception("meter_not_found: $meterName")
