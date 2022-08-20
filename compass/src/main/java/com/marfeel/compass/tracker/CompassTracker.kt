package com.marfeel.compass.tracker

import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.core.PingEmitter

interface CompassTracking {
    fun startPageView(url: String)
    fun stopTracking()
}

//TODO: Internal when DI is ready
object CompassTracker : CompassTracking {

    private val pingEmitter = PingEmitter()
    private val backgroundWatcher = BackgroundWatcher(pingEmitter).apply { initialize() }

    override fun startPageView(url: String) {
        pingEmitter.start(url)
    }

    override fun stopTracking() {
        pingEmitter.stop()
    }
}
