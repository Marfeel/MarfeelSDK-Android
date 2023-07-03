package com.marfeel.demoapp

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
	navigateToXmlNewsRecyclerView: () -> Unit,
) {
	val scaffoldState = rememberScaffoldState()
	val backgroundColor = Color.White
	var showExtendedItem by remember { mutableStateOf(false) }
	val titleStyle = TextStyle.Default.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
	val coroutineScope = CoroutineScope(Dispatchers.IO)

	tracker.setSessionVar("pepe", "pepa")
	tracker.setSessionVar("pepe2", "pepa2")
	tracker.setUserVar("lolo", "lola")
	tracker.setUserVar("lolo2", "lola2")
	tracker.addUserSegment("segment")
	tracker.addUserSegment("another-segment")
	tracker.setUserConsent(true)

	Scaffold(
		Modifier
			.verticalScroll(rememberScrollState())
			.fillMaxSize()
			.background(backgroundColor),
		scaffoldState = scaffoldState,
	) {
		Column(
			Modifier
				.fillMaxSize()
				.background(backgroundColor)
				.padding(horizontal = 20.dp, vertical = 48.dp)
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
						if (showExtendedItem) tracker.trackScreen("expansible screen")
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
					.background(Color(0xFFE06581))
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
					.background(Color(0xFF00FF00))
					.clickable {
						navigateToXmlNewsRecyclerView()
					}
			) {
				Text(
					text = "Noticia XML using Recycler view",
					color = Color.Black,
					style = titleStyle,
					modifier = Modifier.padding(16.dp)
				)
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 32.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = "Conversión",
					style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
					color = Color.Black,
				)

				var conversion by remember { mutableStateOf("") }
				TextField(
					modifier = Modifier.padding(start = 16.dp),
					value = conversion,
					onValueChange = { conversion = it },
					trailingIcon = {
						Icon(
							imageVector = Icons.Rounded.Send,
							contentDescription = "Send",
							Modifier.clickable { tracker.trackConversion(conversion) })
					}
				)
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 32.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = "UserId",
					style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
					color = Color.Black,
				)

				var userId by remember { mutableStateOf("") }
				TextField(
					modifier = Modifier.padding(start = 16.dp),
					value = userId,
					onValueChange = { userId = it },
					trailingIcon = {
						Icon(
							imageVector = Icons.Rounded.Send,
							contentDescription = "Send",
							Modifier.clickable { tracker.setSiteUserId(userId) })
					}
				)
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 32.dp),
				horizontalArrangement = Arrangement.SpaceEvenly
			) {
				FloatingActionButton(
					modifier = Modifier.padding(bottom = 8.dp),
					backgroundColor = Color(0xFF641172),
					onClick = { tracker.stopTracking() }) {
					Text(text = "StopPV", color = Color.White)
				}
				FloatingActionButton(
					backgroundColor = Color(0xFF1A2149),
					onClick = {
						coroutineScope.launch {
							val rfv = tracker.getRFV()
							Log.d("Compass", "$rfv")
						}
					}) {
					Text(text = "RFV", color = Color.White)
				}
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
