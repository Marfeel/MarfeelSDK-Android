package com.marfeel.demoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.marfeel.compass.core.model.multimedia.Event
import com.marfeel.compass.core.model.multimedia.MultimediaMetadata
import com.marfeel.compass.core.model.multimedia.Type
import com.marfeel.compass.tracker.CompassTracking
import com.marfeel.compass.tracker.multimedia.MultimediaTracking
import com.marfeel.demoapp.databinding.ActivityNewsXmlactivityBinding
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView


class NewsXMLActivity : AppCompatActivity() {

	private lateinit var binding: ActivityNewsXmlactivityBinding
	private val tracker: CompassTracking = CompassTracking.getInstance()
	private val multimediaTracker: MultimediaTracking = MultimediaTracking.getInstance()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityNewsXmlactivityBinding.inflate(layoutInflater)

		val scrollView = binding.root

		tracker.trackNewPage("http://dev.marfeel.co/2022/06/24/rem-provident-voluptates-itaque-quis-beatae-ratione/", scrollView)
		setContentView(binding.root)

		val youTubePlayerView = findViewById<YouTubePlayerView>(R.id.youtube_player_view)
		val tracker = YouTubePlayerTracker()
		var isItemDefined = false;

		youTubePlayerView.addYouTubePlayerListener(tracker)

		youTubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
			override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
				when(state) {
					PlayerConstants.PlayerState.PLAYING -> {
						if (!isItemDefined) {
							multimediaTracker.initializeItem(
								tracker.videoId!!,
								"youtube",
								tracker.videoId!!,
								Type.VIDEO,
								MultimediaMetadata(
									false,
									"title",
									"description",
									"url",
									"hutmbnail",
									"authors",
									1234,
									if (tracker.videoDuration.toInt() == 0) 1000000 else tracker.videoDuration.toInt()
								)
							)
							isItemDefined = true
						}
						multimediaTracker.registerEvent(tracker.videoId!!, Event.PLAY, tracker.currentSecond.toInt())
					}
					PlayerConstants.PlayerState.PAUSED -> multimediaTracker.registerEvent(tracker.videoId!!, Event.PAUSE, tracker.currentSecond.toInt())
					PlayerConstants.PlayerState.ENDED -> multimediaTracker.registerEvent(tracker.videoId!!, Event.END, tracker.currentSecond.toInt())
					else -> {}
				}
			}
			override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
				multimediaTracker.registerEvent(tracker.videoId!!, Event.UPDATE_CURRENT_TIME, tracker.currentSecond.toInt())
			}
		})
	}
}
