package com.marfeel.demoapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.marfeel.compass.tracker.CompassTracker
import com.marfeel.demoapp.databinding.ActivityNewsXmlactivityBinding


class NewsXMLActivity : AppCompatActivity() {

	private lateinit var binding: ActivityNewsXmlactivityBinding
	private val tracker: CompassTracker = CompassTracker

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityNewsXmlactivityBinding.inflate(layoutInflater)
		val scrollView = binding.root
		tracker.startPageView("NewsActivity - XML", scrollView)
		setContentView(binding.root)
	}
}
