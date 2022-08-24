package com.marfeel.compass

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.marfeel.compass.core.PingEmitter
import com.marfeel.compass.tracker.CompassTracking


internal class BackgroundWatcher(private val pingEmitter: PingEmitter) : DefaultLifecycleObserver {
    fun initialize() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        pingEmitter.appOnBackground = false
    }

    override fun onPause(owner: LifecycleOwner) {
        pingEmitter.appOnBackground = true
    }
}