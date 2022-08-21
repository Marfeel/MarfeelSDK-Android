package com.marfeel.demoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import com.marfeel.compass.tracker.CompassTracker
import com.marfeel.demoapp.databinding.ActivityNewsXmlactivityBinding

class NewsXMLActivity : AppCompatActivity() {

	private lateinit var binding: ActivityNewsXmlactivityBinding
	private val tracker: CompassTracker = CompassTracker

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		tracker.startPageView("NewsActivity - XML")
		binding = ActivityNewsXmlactivityBinding.inflate(layoutInflater)
		setContentView(binding.root)
	}
}
