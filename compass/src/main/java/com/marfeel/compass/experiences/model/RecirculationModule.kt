package com.marfeel.compass.experiences.model

data class RecirculationLink(
    val url: String,
    val position: Int
)

data class RecirculationModule(
    val name: String,
    val links: List<RecirculationLink>,
    /** A/B test version (`experimentation.name`), sent as `tv` on every link like the web SDK. */
    val testVersion: String? = null,
)
