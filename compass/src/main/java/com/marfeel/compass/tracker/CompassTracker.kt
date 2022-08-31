package com.marfeel.compass.tracker

import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    fun getRFV(onResult: (String?) -> Unit)
    fun trackConversion(conversion: String)

    companion object {
        fun initialize(context: Context, accountId: String) {
            addAndroidContextToDiApplication(context)
            CompassTracker.initialize(accountId)
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
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val initialized: Boolean
        get() = memory.readAccountId() != null

    internal fun initialize(accountId: String) {
        memory.updateAccountId(accountId)
    }

    override fun startPageView(url: String) {
        check(initialized) { compassNotInitializedErrorMessage }
        backgroundWatcher.initialize()
        memory.updatePage(Page(url))
        pingEmitter.start(url)
    }

    override fun startPageView(url: String, scrollView: NestedScrollView) {
        check(initialized) { compassNotInitializedErrorMessage }
        scrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { view, _, scrollY, _, _ ->
                val scrollViewHeight: Double =
                    (view.getChildAt(0).bottom - scrollView.height).toDouble()
                val scrollPosition = scrollY.toDouble() / scrollViewHeight * 100
                (scrollView.getChildAt(0).bottom - scrollView.height).toDouble()
                pingEmitter.updateScrollPercentage(scrollPosition.toScrollPercentage())
            }
        )
        startPageView(url)
    }

    internal fun Double.toScrollPercentage(): Int {
        val range = 0..100
        return when {
            this > range.last -> range.last
            else -> this.toInt()
        }
    }

    override fun stopTracking() {
        check(initialized) { compassNotInitializedErrorMessage }
        pingEmitter.stop()
    }

    override fun setUserId(userId: String) {
        check(initialized) { compassNotInitializedErrorMessage }
        storage.updateUserId(userId)
    }

    override fun setUserType(userType: UserType) {
        check(initialized) { compassNotInitializedErrorMessage }
        storage.updateUserType(userType)
    }

    internal fun updateScrollPercentage(scrollPosition: Int) {
        check(initialized) { compassNotInitializedErrorMessage }
        pingEmitter.updateScrollPercentage(scrollPosition)
    }

    override fun getRFV(): String? =
        getRFV.invoke()

    override fun getRFV(onResult: (String?) -> Unit) {
        check(initialized) { compassNotInitializedErrorMessage }
        coroutineScope.launch {
            val response = getRFV()
            onResult(response)
        }
    }

    override fun trackConversion(conversion: String) {
        check(initialized) { compassNotInitializedErrorMessage }
        memory.addPendingConversion(conversion)
    }
}
