package com.marfeel.compass.core.model.multimedia

import com.google.gson.annotations.SerializedName

enum class Type(val id: String) {
    @SerializedName("audio")
    AUDIO("audio"),
    @SerializedName("video")
    VIDEO("video")
}
