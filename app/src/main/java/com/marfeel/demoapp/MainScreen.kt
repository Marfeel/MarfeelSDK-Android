package com.marfeel.demoapp

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marfeel.compass.tracker.CompassTracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(
	tracker: CompassTracking,
	navigateToComposeNews: () -> Unit,
	navigateToXmlNews: () -> Unit,
	navigateToSettings: () -> Unit,
) {
	val scaffoldState = rememberScaffoldState()
	val backgroundColor = Color.White
	var showExtendedItem by remember { mutableStateOf(false) }
	val titleStyle = TextStyle.Default.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
	val coroutineScope = CoroutineScope(Dispatchers.IO)

	Scaffold(
		Modifier
			.fillMaxSize()
			.background(backgroundColor),
		scaffoldState = scaffoldState,
		floatingActionButton = {
			FloatingActionButton(
				onClick = {
					coroutineScope.launch {
						val rfv = tracker.getRFV()
						Log.d("Compass", "$rfv")
					}
				}) {
				Text(text = "RFV", color = Color.White)
			}
		}
	) {
		Column(
			Modifier
				.fillMaxSize()
				.background(backgroundColor)
				.padding(horizontal = 24.dp, vertical = 48.dp)
		) {
			Text(
				text = "El Diario",
				color = Color.Black,
				style = titleStyle,
				modifier = Modifier.padding(bottom = 32.dp)
			)
			Box(
				Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(4.dp))
					.background(Color(0xFF1231D1))
					.clickable {
						showExtendedItem = !showExtendedItem
						if (!showExtendedItem) tracker.stopTracking()
						else tracker.startPageView("losjavis.com")
					}
			) {
				Column {
					Text(
						text = "Noticia Extensible",
						color = Color.White,
						style = titleStyle,
						modifier = Modifier.padding(16.dp)
					)
					if (showExtendedItem) {
						Text(
							text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
							color = Color.White,
							modifier = Modifier.padding(16.dp),
						)
					}
				}
			}

			Box(
				Modifier
					.fillMaxWidth()
					.padding(top = 32.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(Color(0xFFE06581))
					.clickable {
						navigateToComposeNews()
					}
			) {
				Text(
					text = "Noticia Compose",
					color = Color.White,
					style = titleStyle,
					modifier = Modifier.padding(16.dp)
				)
			}

			Box(
				Modifier
					.fillMaxWidth()
					.padding(top = 32.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(Color(0xFFCCAA44))
					.clickable {
						navigateToXmlNews()
					}
			) {
				Text(
					text = "Noticia XML",
					color = Color.White,
					style = titleStyle,
					modifier = Modifier.padding(16.dp)
				)
			}

			Box(
				Modifier
					.fillMaxWidth()
					.padding(top = 32.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(Color(0xFF5DB948))
					.clickable {
						tracker.stopTracking()
						navigateToSettings()
					}
			) {
				Text(
					text = "Ajustes",
					color = Color.White,
					style = titleStyle,
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
		MainScreen(CompassTracking.getInstance(), {}, {}, {})
	}
}
