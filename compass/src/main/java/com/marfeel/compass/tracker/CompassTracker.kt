package com.marfeel.compass.tracker

import android.view.ViewTreeObserver
import androidx.core.widget.NestedScrollView
import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.core.PingEmitter
import com.marfeel.compass.core.UserType
import com.marfeel.compass.di.CompassKoinComponent
import org.koin.core.component.inject

interface CompassTracking {
	fun startPageView(url: String)
	fun startPageView(url: String, scrollView: NestedScrollView)
	fun stopTracking()
	fun setUserId(userId: String)
	fun setUserType(userType: UserType)
	fun getRFV(): String?

	companion object {
		fun getInstance(): CompassTracking = CompassTracker
	}
}

internal object CompassTracker : CompassTracking, CompassKoinComponent {

    private val pingEmitter: PingEmitter by inject()
    private val backgroundWatcher: BackgroundWatcher by inject()

    override fun startPageView(url: String) {
        backgroundWatcher.initialize()
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

	override fun setUserId(userId: String) {
//		storage.updateUserId(userId)
	}

	override fun setUserType(userType: UserType) {
//		storage.updateUserType(userType)
	}

	override fun getRFV(): String? {
		TODO("Not yet implemented")
	}
}

