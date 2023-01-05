package com.marfeel.demoapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.fragment.app.FragmentActivity
import com.marfeel.compass.tracker.CompassTracking

class MainActivity : FragmentActivity() {

	private val tracker: CompassTracking = CompassTracking.getInstance()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		CompassTracking.initialize(this, "1988")

		setContent {
			MaterialTheme {
				MainScreen(
					tracker = tracker,
					navigateToXmlNews = {
						val intent = Intent(this, NewsXMLActivity::class.java)
						startActivity(intent)
					},
					navigateToComposeNews = {
						val intent = Intent(this, NewsComposeActivity::class.java)
						startActivity(intent)
					},
					navigateToXmlNewsRecyclerView = {
						val intent = Intent(this, RecyclerViewActivity::class.java)
						startActivity(intent)
					},
				)
			}
		}
	}
}
