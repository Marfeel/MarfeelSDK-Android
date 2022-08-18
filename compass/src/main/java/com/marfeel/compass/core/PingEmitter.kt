package com.marfeel.compass.core

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

internal class PingEmitter {

	private val scope = GlobalScope
	private val pingFrequencyInMs = 10000L

	fun start() {
		scope.launch {
			while (true) {
				ping()
				delay(pingFrequencyInMs)
			}
		}
	}

	private fun ping() {
		Timber.d("ping")
	}
}
