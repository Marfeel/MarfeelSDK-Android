package com.marfeel.compass.experiences.model

data class RecirculationLink(
    val url: String,
    val position: String
)

data class RecirculationModule(
    val name: String,
    val links: List<RecirculationLink>
)
