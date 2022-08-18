package com.marfeel.compass.tracker

import com.marfeel.compass.core.PingEmitter

interface CompassTracking {
	fun startPageView(url: String)
	fun stopTracking()
}

class CompassTracker: CompassTracking {

	private val pingEmitter = PingEmitter()

	override fun startPageView(url: String) {
		pingEmitter.start()
	}
	override fun stopTracking() {
		pingEmitter.stop()
	}
}
