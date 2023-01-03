package com.marfeel.demoapp

import android.app.Activity
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.appcompat.app.AppCompatActivity
import com.marfeel.compass.tracker.CompassTracking
import com.marfeel.demoapp.databinding.ActivityNewsXmlactivityBinding
import java.security.AccessController.getContext


class NewsXMLActivity : AppCompatActivity() {

	private lateinit var binding: ActivityNewsXmlactivityBinding
	private val tracker: CompassTracking = CompassTracking.getInstance()
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityNewsXmlactivityBinding.inflate(layoutInflater)
		val scrollView = binding.root
		tracker.startPageView("https://newsactivityxml.com", scrollView)
		setContentView(binding.root)
	}
}
