package com.marfeel.compass.experiences.model

enum class ExperienceFamily(val key: String) {
	TWITTER("twitterexperience"),
	FACEBOOK("facebookexperience"),
	YOUTUBE("youtubeexperience"),
	RECOMMENDER("recommenderexperience"),
	TELEGRAM("telegramexperience"),
	GATHERING("gatheringexperience"),
	AFFILIATE("affiliateexperience"),
	PODCAST("podcastexperience"),
	EXPERIMENTATION("experimentsexperience"),
	WIDGET("widgetexperience"),
	MARFEEL_PASS("passexperience"),
	SCRIPT("scriptexperience"),
	PAYWALL("paywallexperience"),
	MARFEEL_SOCIAL("marfeelsocial"),
	UNKNOWN("unknown");

	companion object {
		fun fromKey(key: String): ExperienceFamily =
			values().find { it.key == key } ?: UNKNOWN
	}
}
