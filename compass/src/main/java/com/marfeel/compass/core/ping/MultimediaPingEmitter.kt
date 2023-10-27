package com.marfeel.compass.core.ping

import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.multimedia.MultimediaPingData
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.tracker.multimedia.MultimediaItem
import com.marfeel.compass.usecase.GetRFV
import com.marfeel.compass.usecase.MultimediaPing
import kotlinx.coroutines.*

internal typealias PingFn = (item: MultimediaPingData, onFinish: () -> Unit) -> Unit

internal fun throttle(
    intervalMs: Long,
    destinationFunction: PingFn
): PingFn {
    var throttleJob: Job? = null
    var latestParams: Pair<MultimediaPingData, () -> Unit>

    return { d, onFinish ->
        latestParams = Pair(d, onFinish)

        if (throttleJob?.isCompleted != false) {
            throttleJob = CoroutineScope(Dispatchers.IO).launch {
                delay(intervalMs)
                latestParams.let { destinationFunction(it.first, it.second) }
            }
        }
    }
}

internal class MultimediaPingEmitter(private val doPing: MultimediaPing) {

    private val pingFrequencyInMs = 5000L
    private var pingRegistry:HashMap<String, Pair<Int, PingFn>> = hashMapOf()
    private val getRFV: GetRFV by lazy { CompassComponent.getRFV() }

    fun ping(item: MultimediaItem) {
        val trackingFn:PingFn = { d, onFinish ->
            d.rfv = getRFV()
            doPing(d)
            onFinish()
        }
        val pingState = pingRegistry[item.id]
        val batchedPing = pingState?.second ?: throttle(pingFrequencyInMs, trackingFn)
        val counter = pingState?.first ?: 0
        val data = doPing.getData(MultimediaPingEmitterState(counter, item))

        if (pingState == null) {
            pingRegistry[item.id] = Pair(counter, batchedPing)
        }

        data?.let {
            batchedPing(it) {
                val pingState = pingRegistry[item.id]

                if (pingState != null) {
                    pingRegistry[item.id] = pingState.copy(first = pingState.first + 1)
                }
            }
        }
    }

    fun reset() {
        pingRegistry.clear()
    }
}

internal data class MultimediaPingEmitterState(
    val pingCounter: Int,
    val item: MultimediaItem,
)
