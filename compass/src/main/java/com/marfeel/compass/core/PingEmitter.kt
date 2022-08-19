package com.marfeel.compass.core

import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

internal class PingEmitter {
	private val pingFrequencyInMs = 1000L
	private val scope = GlobalScope
	private var job: Job? = null
	private var isRunning: Boolean = false

	fun start() {
		isRunning = true

		job = scope.launch {
			while (true) {
				ping()
				delay(pingFrequencyInMs)
			}
		}
	}

	private fun ping() {
		Log.d("xtest", "ping")
		Timber.d("ping")
	}

	fun stop() {
		job?.cancel()
		job = null
		isRunning = false
	}
}
