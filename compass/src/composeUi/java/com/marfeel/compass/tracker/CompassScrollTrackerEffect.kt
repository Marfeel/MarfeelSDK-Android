package com.marfeel.compass.tracker

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.marfeel.compass.tracker.CompassTracker.toScrollPercentage

/**
 * function to track scroll percentage when using compose.
 *
 * This function should be used with [CompassTracking.startPageView].
 * @param scrollState the scroll state of the composable container showing the page content.
 */
@Composable
fun CompassScrollTrackerEffect(scrollState: ScrollState) {
	LaunchedEffect(scrollState.value) {
		val scrollPercentage =
			with(scrollState) { value.toDouble() / maxValue * 100 }.toScrollPercentage()
		CompassTracker.updateScrollPercentage(scrollPercentage)
	}
}