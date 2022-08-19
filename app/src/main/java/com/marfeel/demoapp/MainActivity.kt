package com.marfeel.demoapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.fragment.app.FragmentActivity
import com.marfeel.compass.tracker.CompassTracker

class MainActivity : FragmentActivity() {
	private val tracker: CompassTracker = CompassTracker()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			MaterialTheme {
				MainScreen(tracker)
			}
		}
	}
}
