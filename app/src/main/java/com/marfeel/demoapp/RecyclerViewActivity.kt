package com.marfeel.demoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.marfeel.compass.tracker.CompassTracking


class RecyclerViewActivity : AppCompatActivity() {

	private val tracker: CompassTracking = CompassTracking.getInstance()
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.recycler_view_activity)

		val recyclerview = findViewById<RecyclerView>(R.id.recyclerview)

		recyclerview.layoutManager = LinearLayoutManager(this)

		val data = ArrayList<ItemsViewModel>()

		for (i in 1..20) {
			data.add(ItemsViewModel(com.google.android.material.R.drawable.ic_clock_black_24dp, "Item " + i))
		}

		val adapter = CustomAdapter(data)

		recyclerview.adapter = adapter

		tracker.startPageView("https://newsactivityxml-recycler-view.com", recyclerview)
	}
}
