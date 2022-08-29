package com.marfeel.compass.tracker

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.marfeel.compass.tracker.CompassTracker.toScrollPercentage

@Composable
fun CompassScrollTrackerEffect(scrollState: ScrollState){
    LaunchedEffect(scrollState){
        val scrollPercentage =
            with(scrollState) { value.toDouble() / maxValue * 100 }.toScrollPercentage()
        CompassTracker.updateScrollPercentage(scrollPercentage)
    }
}