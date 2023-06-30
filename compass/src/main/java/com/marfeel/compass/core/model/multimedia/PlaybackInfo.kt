package com.marfeel.compass.core.model.multimedia

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.registerPingDataSerializer
import java.lang.reflect.Type

internal data class PlaybackInfo(
    @SerializedName("ads")
    val ads: MutableList<Int> = mutableListOf(),
    @SerializedName("mp")
    var maxPlayTime: Int = 0,
    @SerializedName("play")
    val play: MutableList<Int> = mutableListOf(),
    @SerializedName("pause")
    val pause: MutableList<Int> = mutableListOf(),
    @SerializedName("mute")
    val mute: MutableList<Int> = mutableListOf(),
    @SerializedName("unmute")
    val unmute: MutableList<Int> = mutableListOf(),
    @SerializedName("bscr")
    val backScreen: MutableList<Int> = mutableListOf(),
    @SerializedName("fscr")
    val fullScreen: MutableList<Int> = mutableListOf(),
    @Transient
    var inViewport: Boolean = false,
    @SerializedName("e")
    var ended: Boolean = false
) {
    /* kotlin does not serialize computed values https://github.com/Kotlin/kotlinx.serialization/issues/805, so,
     * it needs to be updated by hand instead of via getter
     * */
    @SerializedName("s")
    var started = false
    @SerializedName("a")
    var adsStarted = false
    @SerializedName("ap")
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

private class BooleanSerializer : JsonSerializer<Boolean> {
    override fun serialize(src: Boolean, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(if(src) 1 else 0)
    }
}

internal class PlaybackInfoSerializer : JsonSerializer<PlaybackInfo> {
    private val gson: Gson by lazy {
        GsonBuilder()
            .registerPingDataSerializer()
            .registerTypeAdapter(Boolean::class.javaObjectType, BooleanSerializer())
            .create()
    }

    override fun serialize(src: PlaybackInfo, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        return gson.toJsonTree(src)
    }
}