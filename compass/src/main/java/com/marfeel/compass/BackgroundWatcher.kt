package com.marfeel.compass

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.marfeel.compass.core.PingEmitter
import com.marfeel.compass.tracker.CompassTracking


internal interface BackgroundWatcher : DefaultLifecycleObserver {
    var appOnBackground: Boolean
    var lastBackgroundTimeStamp: Long?

    fun startBackgroundWatcher() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun stopBackgroundWatcher() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}