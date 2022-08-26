package com.marfeel.compass.tracker

import android.content.Context
import android.view.ViewTreeObserver
import androidx.core.widget.NestedScrollView
import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.core.Page
import com.marfeel.compass.core.PingEmitter
import com.marfeel.compass.core.UserType
import com.marfeel.compass.di.CompassKoinComponent
import com.marfeel.compass.di.addAndroidContextToDiApplication
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.usecase.GetRFV
import com.marfeel.compass.usecase.Ping
import org.koin.core.component.inject

private const val compassNotInitializedErrorMessage =
	"Compass not initialized. Make sure CompassTracking::initialize has been called"

interface CompassTracking {
	fun startPageView(url: String)
	fun startPageView(url: String, scrollView: NestedScrollView)
	fun stopTracking()
	fun setUserId(userId: String)
	fun setUserType(userType: UserType)
	fun getRFV(): String?

	companion object {
		internal var accountId: String? = null
		fun initialize(context: Context, accountId: String) {
			addAndroidContextToDiApplication(context)
			this.accountId = accountId
		}

		fun getInstance(): CompassTracking = CompassTracker
	}
}

internal object CompassTracker : CompassTracking, CompassKoinComponent {

	private val pingEmitter: PingEmitter by inject()
	private val backgroundWatcher: BackgroundWatcher by inject()
	private val storage: Storage by inject()
	private val memory: Memory by inject()
	private val getRFV: GetRFV by inject()

	override fun startPageView(url: String) {
		requireNotNull(CompassTracking.accountId)
		backgroundWatcher.initialize()
		memory.updatePage(Page(url))
		pingEmitter.start(url)
	}

	override fun startPageView(url: String, scrollView: NestedScrollView) {
		check(CompassTracking.accountId != null) { compassNotInitializedErrorMessage }
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
		check(CompassTracking.accountId != null) { compassNotInitializedErrorMessage }
		pingEmitter.stop()
	}

	override fun setUserId(userId: String) {
		check(CompassTracking.accountId != null) { compassNotInitializedErrorMessage }
		storage.updateUserId(userId)
	}

	override fun setUserType(userType: UserType) {
		requireNotNull(CompassTracking.accountId)
		storage.updateUserType(userType)
	}

	override fun getRFV(): String? =
		getRFV.invoke()
}
