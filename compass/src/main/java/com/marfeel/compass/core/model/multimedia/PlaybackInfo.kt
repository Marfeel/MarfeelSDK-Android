package com.marfeel.compass.core.model.multimedia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
internal data class PlaybackInfo(
    @SerialName("ads")
    val ads: MutableList<Int> = mutableListOf(),
    @SerialName("mp")
    var maxPlayTime: Int = 0,
    @SerialName("play")
    val play: MutableList<Int> = mutableListOf(),
    @SerialName("pause")
    val pause: MutableList<Int> = mutableListOf(),
    @SerialName("mute")
    val mute: MutableList<Int> = mutableListOf(),
    @SerialName("unmute")
    val unmute: MutableList<Int> = mutableListOf(),
    @SerialName("bscr")
    val backScreen: MutableList<Int> = mutableListOf(),
    @SerialName("fscr")
    val fullScreen: MutableList<Int> = mutableListOf(),
    @Transient
    var inViewport: Boolean = false,
    @SerialName("e")
    var ended: Boolean = false
) {
    /* kotlin does not serialize computed values https://github.com/Kotlin/kotlinx.serialization/issues/805, so,
     * it needs to be updated by hand instead of via getter
     * */
    @SerialName("s")
    var started = false
    @SerialName("a")
    var adsStarted = false
    @SerialName("ap")
    var adsLength = 0
    fun addEvent(event: Event, time: Int) {
        when (event) {
            Event.PLAY -> {
                play.add(time)
                started = true
            }
            Event.PAUSE -> pause.add(time)
            Event.END -> {
                pause.add(time)
                ended = true
            }
            Event.AD_PLAY -> {
                ads.add(time)
                adsStarted = true
                adsLength = ads.size
            }
            Event.MUTE -> mute.add(time)
            Event.UNMUTE -> unmute.add(time)
            Event.FULL_SCREEN -> fullScreen.add(time)
            Event.BACK_SCREEN -> backScreen.add(time)
            Event.ENTER_VIEWPORT -> inViewport = true
            Event.LEAVE_VIEWPORT -> inViewport = false
            else -> {}
        }

        if (time > maxPlayTime) {
            maxPlayTime = time
        }
    }
}

