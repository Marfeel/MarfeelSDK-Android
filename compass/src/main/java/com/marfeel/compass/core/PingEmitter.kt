package com.marfeel.compass.core

import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

internal class PingEmitter {
	private val pingFrequencyInMs = 10000L
	private val scope = GlobalScope
	private var job: Job? = null
	private var pingEmitterState: PingEmitterState? = null
	var appOnBackground: Boolean = false

	fun start(
		url: String,
		scrollPosition: Int? = null
	) {
		pingEmitterState = PingEmitterState(
			url = url,
			pingCounter = 0,
			scrollPercent = scrollPosition,
			pageStartTimeStamp = System.currentTimeMillis()
		)

		job = scope.launch {
			while (!appOnBackground) {
				ping()
				delay(pingFrequencyInMs)
			}
		}
	}

	private fun ping() {
//		val userId = storage.
		pingEmitterState?.let {
			pingEmitterState = it.copy(pingCounter = it.pingCounter + 1)
		}
		Log.d("xtest", "ping \n scrollPercentage: ${pingEmitterState?.scrollPercent} \n")
		Timber.d("Timber ping")
	}

	fun stop() {
		Log.d("xtest", "pingStop")
		job?.cancelChildren()
//		job = null
		pingEmitterState = null
	}

	fun updateScrollPercentage(scrollPosition: Int) {
		pingEmitterState = pingEmitterState?.copy(scrollPercent = scrollPosition)
	}
}

internal data class PingEmitterState(
	val url: String,
	val pingCounter: Long,
	val scrollPercent: Int?,
	val pageStartTimeStamp: Long
)
