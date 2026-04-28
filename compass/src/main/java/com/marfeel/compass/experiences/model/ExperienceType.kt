package com.marfeel.compass.experiences.model

enum class ExperienceType(val key: String) {
	INLINE("inline"),
	FLOWCARDS("flowcards"),
	COMPASS("compass"),
	AD_MANAGER("adManager"),
	AFFILIATION_ENHANCER("affiliationEnhancer"),
	CONVERSIONS("conversions"),
	CONTENT("content"),
	EXPERIMENTS("experiments"),
	EXPERIMENTATION("experimentation"),
	RECIRCULATION("recirculation"),
	GOAL_TRACKING("goalTracking"),
	ECOMMERCE("ecommerce"),
	MULTIMEDIA("multimedia"),
	PIANO("piano"),
	APP_BANNER("appBanner"),
	UNKNOWN("unknown");

	companion object {
		fun fromKey(key: String): ExperienceType? = values().find { it.key == key }
	}
}
