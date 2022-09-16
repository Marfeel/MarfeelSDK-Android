package com.marfeel.demoapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.marfeel.compass.tracker.CompassScrollTrackerEffect
import com.marfeel.compass.tracker.CompassTracking

class NewsComposeActivity : FragmentActivity() {
	private val tracker: CompassTracking = CompassTracking.getInstance()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		tracker.startPageView("https://newsactivitycompose.com")
		setContent {
			MaterialTheme {
				NewsScreen()
			}
		}
	}

	@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
	@Composable
	private fun NewsScreen() {
		val scrollState = rememberScrollState()
		CompassScrollTrackerEffect(scrollState)
		Scaffold(
			Modifier
				.fillMaxSize()
				.background(Color.White)
		) {
			Column(
				Modifier
					.fillMaxSize()
					.padding(horizontal = 24.dp, vertical = 48.dp)
					.verticalScroll(scrollState)
			) {
				Text(
					text = "Noticia Compose",
					color = Color.Black,
					style = TextStyle.Default.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
					modifier = Modifier.padding(bottom = 32.dp)
				)

				Paragraph()
				Paragraph()
				Paragraph()
				Paragraph()
				Paragraph()
				Paragraph()
			}
		}
	}

	@Composable
	private fun Paragraph() {
		Text(
			text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
			color = Color.Black,
			modifier = Modifier.padding(top = 16.dp),
		)
	}

	@Preview
	@Composable
	fun NewsScreenPreview() {
		MaterialTheme {
			NewsScreen()
		}
	}
}

