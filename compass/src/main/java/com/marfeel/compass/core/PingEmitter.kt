package com.marfeel.compass.core

import android.util.Log
import com.marfeel.compass.usecase.Ping
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

internal class PingEmitter(
    private val doPing: Ping,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
) {

    private val pingFrequencyInMs = 10000L
    private val job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(coroutineContext + job)
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
        Log.d("xtest", "pingStop")
        job.cancel()
        //job.cancelChildren()
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
