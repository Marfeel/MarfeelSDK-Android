package com.marfeel.compass.core

import androidx.lifecycle.LifecycleOwner
import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.usecase.Ping
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

internal class PingEmitter(
    private val doPing: Ping,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
) : BackgroundWatcher {

    private val pingFrequencyInMs = 10000L
    private val job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(coroutineContext + job)
    private var pingEmitterState: PingEmitterState? = null

    override var appOnBackground: Boolean = false
    override var lastBackgroundTimeStamp: Long? = null

    fun start(
        url: String,
        scrollPosition: Int? = null
    ) {
        lastBackgroundTimeStamp = null
        startBackgroundWatcher()
        pingEmitterState = PingEmitterState(
            url = url,
            pingCounter = 0,
            scrollPercent = scrollPosition,
            pageStartTimeStamp = currentTimeStampInSeconds(),
            timeOnBackground = 0,
        )

        scope.launch {
            while (true) {
                if (!appOnBackground)
                    launch(Dispatchers.IO) { ping() }
                delay(pingFrequencyInMs)
            }
        }
    }

    private fun ping() {
        pingEmitterState?.let {
            pingEmitterState = it.copy(pingCounter = it.pingCounter + 1)
            doPing(it)
        }
    }

    fun stop() {
        stopBackgroundWatcher()
        job.cancel()
        pingEmitterState = null
    }

    fun updateScrollPercentage(scrollPosition: Int) {
        pingEmitterState = pingEmitterState?.copy(scrollPercent = scrollPosition)
    }

    override fun onResume(owner: LifecycleOwner) {
        appOnBackground = false
        val timeStamp = lastBackgroundTimeStamp
        if (timeStamp != null)
            pingEmitterState =
                pingEmitterState?.addTimeOnBackground(currentTimeStampInSeconds() - timeStamp)
        lastBackgroundTimeStamp = null
    }

    override fun onPause(owner: LifecycleOwner) {
        appOnBackground = true
        lastBackgroundTimeStamp = currentTimeStampInSeconds()
    }
}

internal data class PingEmitterState(
    val url: String,
    val pingCounter: Int,
    val scrollPercent: Int?,
    val pageStartTimeStamp: Long,
    val timeOnBackground: Long,
) {
    val activeTimeOnPage = currentTimeStampInSeconds() - pageStartTimeStamp - timeOnBackground

    fun addTimeOnBackground(timeOnBackground: Long): PingEmitterState {
        return this.copy(timeOnBackground = this.timeOnBackground + timeOnBackground)
    }
}
