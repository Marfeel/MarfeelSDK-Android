package com.marfeel.demoapp

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.marfeel.compass.tracker.CompassTracker

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(
	tracker: CompassTracker
) {
	val scaffoldState = rememberScaffoldState()
	val backgroundColor = Color.White

	Scaffold(
		Modifier
			.fillMaxSize()
			.background(backgroundColor),
		scaffoldState = scaffoldState
	) {
		Column(
			Modifier
				.fillMaxSize()
				.background(backgroundColor)
				.padding(horizontal = 24.dp, vertical = 48.dp)
		) {
			Box(
				Modifier
					.fillMaxWidth()
					.background(Color.Blue)
					.clickable {
						tracker.startPageView("losjavis.com")
					}
			) {
				Text(
					text = "Start",
					color = Color.White,
					modifier = Modifier.padding(16.dp)
				)
			}

			Box(
				Modifier
					.fillMaxWidth()
					.padding(top = 32.dp)
					.background(Color.Blue)
					.clickable {
						tracker.stopTracking()
					}
			) {
				Text(
					text = "Stop",
					color = Color.White,
					modifier = Modifier.padding(16.dp)
				)
			}
		}
	}
}

@Preview
@Composable
fun MainScreenPreview() {
	MaterialTheme {
		MainScreen(CompassTracker())
	}
}
