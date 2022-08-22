package com.marfeel.demoapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

class SettingsActivity : FragmentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			MaterialTheme {
				SettingsScreen()
			}
		}
	}

	@Composable
	private fun SettingsScreen() {
		Text(
			text = "Ajustes",
			color = Color.Black,
			style = TextStyle.Default.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
			modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp)
		)
	}
}
