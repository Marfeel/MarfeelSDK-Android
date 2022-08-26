package com.marfeel.compass.core

import android.util.Log
import com.marfeel.compass.di.CompassKoinComponent
import com.marfeel.compass.usecase.Ping
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.inject

internal class PingEmitter(private val doPing: Ping) {
	private val pingUseCase: Ping by inject()
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
		pingEmitterState?.let {
			pingEmitterState = it.copy(pingCounter = it.pingCounter + 1)
			pingUseCase(it)
		}
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
