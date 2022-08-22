package com.marfeel.compass.tracker

import android.util.Log
import android.view.ViewTreeObserver
import androidx.core.widget.NestedScrollView
import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.core.PingEmitter

interface CompassTracking {
	fun startPageView(url: String)
	fun startPageView(url: String, scrollView: NestedScrollView)
	fun stopTracking()
}

//TODO: Internal when DI is ready
object CompassTracker : CompassTracking {

	private val pingEmitter = PingEmitter()
	private val backgroundWatcher = BackgroundWatcher(pingEmitter).apply { initialize() }

	override fun startPageView(url: String) {
		pingEmitter.start(url)
	}

	override fun startPageView(url: String, scrollView: NestedScrollView) {
		scrollView.viewTreeObserver.addOnScrollChangedListener(ViewTreeObserver.OnScrollChangedListener {
			val scrollViewHeight: Double =
				(scrollView.getChildAt(0).bottom - scrollView.height).toDouble()
			val getScrollY: Double = scrollView.scrollY.toDouble()
			val scrollPosition = getScrollY / scrollViewHeight * 100.0
			pingEmitter.updateScrollPercentage(scrollPosition.toScrollPercentage())
		})

		startPageView(url)
	}

	private fun Double.toScrollPercentage(): Int {
		val range = 0..100
		return when {
			this > range.last -> range.last
			else -> this.toInt()
		}
	}

	override fun stopTracking() {
		pingEmitter.stop()
	}
}
