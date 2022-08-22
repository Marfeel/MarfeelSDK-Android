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
	private var isRunning: Boolean = false
	private var scrollPercentage: Int? = null
	var appOnBackground: Boolean = false

	fun start(
		url: String,
		scrollPosition: Int? = null
	) {
		Log.d("xtest", "pingStart: $url")
		isRunning = true
		scrollPercentage = scrollPosition

		job = scope.launch {
			while (!appOnBackground) {
				ping()
				delay(pingFrequencyInMs)
			}
		}
	}

	private fun ping() {
		Log.d("xtest", "ping \n scrollPercentage: $scrollPercentage \n")
		Timber.d("Timber ping")
	}

	fun stop() {
		Log.d("xtest", "pingStop")
		job?.cancelChildren()
//		job = null
		isRunning = false
		scrollPercentage = null
	}

	fun updateScrollPercentage(scrollPosition: Int) {
		scrollPercentage = scrollPosition
	}
}
