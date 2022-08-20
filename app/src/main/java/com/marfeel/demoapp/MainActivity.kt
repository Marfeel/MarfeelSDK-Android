package com.marfeel.demoapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.fragment.app.FragmentActivity
import com.marfeel.compass.tracker.CompassTracker

class MainActivity : FragmentActivity() {
	private val tracker: CompassTracker = CompassTracker

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			MaterialTheme {
				MainScreen(
					tracker = tracker,
					navigateToExternalNews = {
						val intent = Intent(this, NewsActivity::class.java)
						startActivity(intent)
					},
					navigateToSettings = {
						val intent = Intent(this, SettingsActivity::class.java)
						startActivity(intent)
					}
				)
			}
		}
	}
}
