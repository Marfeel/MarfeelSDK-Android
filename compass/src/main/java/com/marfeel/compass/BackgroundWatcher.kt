package com.marfeel.compass

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner


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