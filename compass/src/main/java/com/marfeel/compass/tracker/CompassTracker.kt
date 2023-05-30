package com.marfeel.compass.tracker

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.ScrollingView
import androidx.recyclerview.widget.RecyclerView
import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.core.ping.IngestPingEmitter
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.tracker.multimedia.MultimediaTracking
import com.marfeel.compass.usecase.GetRFV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal const val compassNotInitializedErrorMessage =
    "Compass not initialized. Make sure CompassTracking::initialize has been called"

/**
 * CompassTracking is the entry point for all interactions with the library.
 * To access to CompassTracking just retrieve its singleton instance by calling [CompassTracking.getInstance].
 *
 */
interface CompassTracking {
    /**
     * @see trackNewPage(String)
     */
    @Deprecated("Use trackNewPage(url) method", replaceWith = ReplaceWith("trackNewPage(url)"))
    fun startPageView(url: String)

    /**
     * @see trackNewPage(String, ScrollView)
     */
    @Deprecated("Use trackNewPage(url, scrollView) method", replaceWith = ReplaceWith("trackNewPage(url, scrollView)"))
    fun startPageView(url: String, scrollView: ScrollView)

    /**
     * @see trackNewPage(String, ScrollView)
     */
    @Deprecated("Use trackNewPage(url, scrollView) method", replaceWith = ReplaceWith("trackNewPage(url, scrollView)"))
    fun startPageView(url: String, scrollView: RecyclerView)

    /**
     * @see trackNewPage(String, ScrollView)
     */
    @Deprecated("Use trackNewPage(url, scrollView) method", replaceWith = ReplaceWith("trackNewPage(url, scrollView)"))
    fun<T> startPageView(url: String, scrollView: T) where T: FrameLayout, T: ScrollingView


    /**
     * Starts to track the time a user remains on the page given by the [url] parameter as well as the scroll percentage of the content.
     *
     * @param url the url of the page being tracked.
     * @param scrollView view showing the url content.
     */
    fun trackNewPage(url: String, scrollView: ScrollView)

    /**
     * @see trackNewPage(String, ScrollView)
     */
    fun trackNewPage(url: String, scrollView: RecyclerView)

    /**
     * @see trackNewPage(String, ScrollView)
     */
    fun<T> trackNewPage(url: String, scrollView: T) where T: FrameLayout, T: ScrollingView

    /**
     * Starts to track the time a user remains on a page given by the [url] parameter.
     *
     * @param url the url of the page being tracked.
     */
    fun trackNewPage(url: String)

    /**
     * Stops the tracking.
     */
    fun stopTracking()

    /**
     * Associates the user of the application with the records generated by Compass.
     * @param userId the user's identifier in your platform
     */
    @Deprecated("Use setSiteUserId(userID) method", replaceWith = ReplaceWith("setSiteUserId(userId)"))
    fun setUserId(userId: String)

    /**
     * Associates the user of the application with the records generated by Compass.
     * @param userId the user's identifier in your platform
     */
    fun setSiteUserId(userId: String)

    /**
     * Sets the user type.
     * @param userType The user type.
     */
    fun setUserType(userType: UserType)

    /**
     * Gets the RFV code from Compass.
     *
     * This function should not be called from the main thread, **if it is called from the main thread, an exception is thrown**.
     */
    fun getRFV(): String?

    /**
     * Gets the RFV code from Compass asynchronously.
     *
     * @param onResult the callback function to handle te result.
     */
    fun getRFV(onResult: (String?) -> Unit)

    fun trackConversion(conversion: String)

    /**
     * Sets variables for the current page view.
     *
     * @param name variable name
     * @param value variable value
     */
    fun setPageVar(name: String, value: String)

    /**
     * Sets variables for the current session.
     *
     * @param name variable name
     * @param value variable value
     */
    fun setSessionVar(name: String, value: String)

    /**
     * Sets persistent variables for the user.
     *
     * @param name variable name
     * @param value variable value
     */
    fun setUserVar(name: String, value: String)

    /**
     * Sets persistent user segment for the user.
     *
     * @param name user segment name
     */
    fun setUserSegment(name: String)

    /**
     * Sets persistent user segments for the user, overriding previous ones.
     *
     * @param segments user segments names
     */
    fun setUserSegments(segments: List<String>)

    /**
     * removes user segment for the user.
     *
     * @param name user segment name
     */
    fun removeUserSegment(name: String)

    /**
     * Clears all user segments for the user.
     */
    fun clearUserSegments()

    companion object {
        /**
         * Prepare the Compass SDK to track the pages.
         *
         * Typically you should initialize the Compass SDK from your Application class.
         * @param context The Android Context.
         * @param accountId Compass account id.
         */
        fun initialize(context: Context, accountId: String) {
            CompassComponent.context = context.applicationContext
            if (!CompassTracker.initialized) {
                CompassTracker.initialize(accountId)
            }
        }

        /**
         *
         * @return The singleton instance of the CompassTracking interface
         */
        fun getInstance(): CompassTracking = CompassTracker

        /**
         *
         * @return If compass instance has been initialized properly or not
         */
        internal val initialized: Boolean
            get() = CompassTracker.initialized
    }
}

internal object CompassTracker : CompassTracking {

    private val pingEmitter: IngestPingEmitter by lazy { CompassComponent.ingestPingEmitter }
    private val storage: Storage by lazy { CompassComponent.storage }
    private val memory: Memory by lazy { CompassComponent.memory }
    private val getRFV: GetRFV by lazy { CompassComponent.getRFV() }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)


    internal val initialized: Boolean
        get() = memory.readAccountId() != null

    internal fun initialize(accountId: String) {
        memory.updateAccountId(accountId)
        memory.updateSession()
    }

    @Deprecated("Use trackNewPage(url) method", replaceWith = ReplaceWith("trackNewPage(url)"))
    override fun startPageView(url: String) {
        trackNewPage(url)
    }

    override fun trackNewPage(url: String) {
        check(initialized) { compassNotInitializedErrorMessage }
        memory.updatePage(Page(url))
        memory.clearPageVars()
        pingEmitter.start(url)
    }

    @Deprecated("Use trackNewPage(url, scrollView) method", replaceWith = ReplaceWith("trackNewPage(url, scrollView)"))
    override fun<T> startPageView(url: String, scrollView: T) where T: FrameLayout, T: ScrollingView {
        trackNewPage(url, scrollView)
    }

    @Deprecated("Use trackNewPage(url, scrollView) method", replaceWith = ReplaceWith("trackNewPage(url, scrollView)"))
    override fun startPageView(url: String, scrollView: ScrollView) {
        trackNewPage(url, scrollView)
    }

    @Deprecated("Use trackNewPage(url, scrollView) method", replaceWith = ReplaceWith("trackNewPage(url, scrollView)"))
    override fun startPageView(url: String, scrollView: RecyclerView) {
        trackNewPage(url, scrollView)
    }

    override fun<T> trackNewPage(url: String, scrollView: T) where T: FrameLayout, T: ScrollingView {
        trackFrameLayoutPageView(url, scrollView)
    }

    override fun trackNewPage(url: String, scrollView: ScrollView) {
        trackFrameLayoutPageView(url, scrollView)
    }

    private fun<T> trackFrameLayoutPageView(url: String, scrollView: T) where T: FrameLayout {
        trackNewPage(url, scrollView, fun(view: T, scroll: Int, _: Int): Double {
            val scrollViewHeight = (view.getChildAt(0).bottom - scrollView.height).toDouble()

            return scroll.toDouble() / scrollViewHeight * 100
        })
    }

    override fun trackNewPage(url: String, scrollView: RecyclerView) {
        var currentScroll = 0

        trackNewPage(url, scrollView, fun(view: RecyclerView, scroll: Int, oldScroll: Int): Double {
                val scrollViewHeight =
                    (view.computeVerticalScrollRange() - view.computeVerticalScrollExtent()).toDouble()
                val dScroll = scroll - oldScroll
                currentScroll += dScroll

                return currentScroll / scrollViewHeight * 100
            }
        )
    }

    private fun<T> trackNewPage(
        url: String,
        scrollView: T,
        scrollMeasurer: (view: T, scrollY: Int, oldScrollY: Int) -> Double)
    where T: ViewGroup {
        check(initialized) { compassNotInitializedErrorMessage }
        scrollView.setOnScrollChangeListener { view, _, scrollY, _, oldScrollY ->
            view?.let {
                @Suppress("UNCHECKED_CAST")
                val scrollPosition =
                    scrollMeasurer(view as T, scrollY, oldScrollY).toScrollPercentage()

                pingEmitter.updateScrollPercentage(scrollPosition)
            }
        }

        trackNewPage(url)
        MultimediaTracking.reset()
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

    @Deprecated("Use setSiteUserId(userID) method", replaceWith = ReplaceWith("setSiteUserId(userId)"))
    override fun setUserId(userId: String) {
        setSiteUserId(userId)
    }

    override fun setSiteUserId(userId: String) {
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
        getRFV.invoke()?.toString()

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

    override fun setPageVar(name: String, value: String) {
        check(initialized) { compassNotInitializedErrorMessage }

        memory.addPageVar(name, value)
    }

    override fun setSessionVar(name: String, value: String) {
        check(initialized) { compassNotInitializedErrorMessage }

        memory.addSessionVar(name, value)
    }

    override fun setUserVar(name: String, value: String) {
        check(initialized) { compassNotInitializedErrorMessage }

        storage.setUserVar(name, value)
    }

    override fun setUserSegment(name: String) {
        storage.setUserSegment(name)
    }

    override fun setUserSegments(segments: List<String>) {
        storage.setUserSegment(segments)
    }

    override fun removeUserSegment(name: String) {
        storage.removeUserSegment(name)
    }

    override fun clearUserSegments() {
        storage.clearUserSegments()
    }
}
