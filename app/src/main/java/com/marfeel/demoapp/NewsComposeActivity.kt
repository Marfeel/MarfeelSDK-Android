package com.marfeel.demoapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.marfeel.compass.experiences.Recirculation
import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.tracker.CompassScrollTrackerEffect
import com.marfeel.compass.tracker.CompassTracking

private const val MODULE_NAME = "recommended-articles"

private data class ArticleItem(val title: String, val url: String, val position: Int)

private val articles = listOf(
	ArticleItem("The Future of Urban Gardening", "https://example.com/urban-gardening", 0),
	ArticleItem("How Algorithms Shape What We Read", "https://example.com/algorithms-reading", 1),
	ArticleItem("A Brief History of Timekeeping", "https://example.com/timekeeping-history", 2),
	ArticleItem("Why Ocean Currents Matter More Than You Think", "https://example.com/ocean-currents", 3),
)

private val recirculationLinks = articles.map { RecirculationLink(url = it.url, position = it.position) }

class NewsComposeActivity : FragmentActivity() {
	private val tracker: CompassTracking = CompassTracking.getInstance()
	private val recirculation: Recirculation = Recirculation.getInstance()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		tracker.trackNewPage("https://newsactivitycompose.com")
		tracker.trackConversion("conv_1")
		tracker.trackConversion("conv_2")
		tracker.trackConversion("conv_3")

		recirculation.trackEligible(MODULE_NAME, recirculationLinks)
		Log.d("Recirculation", "trackEligible: $MODULE_NAME")

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
		val density = LocalDensity.current
		val screenHeightDp = LocalConfiguration.current.screenHeightDp
		val viewHeight = with(density) { screenHeightDp.dp.toPx() }
		val impressedCards = remember { mutableStateMapOf<Int, Boolean>() }
		val cardContentOffsets = remember { mutableMapOf<Int, Float>() }

		LaunchedEffect(scrollState) {
			snapshotFlow { scrollState.value }.collect { scrollY ->
				cardContentOffsets.forEach { (position, contentTop) ->
					if (impressedCards[position] == true) return@forEach
					val screenTop = contentTop - scrollY
					if (screenTop < viewHeight && screenTop > 0) {
						impressedCards[position] = true
						recirculation.trackImpression(MODULE_NAME, recirculationLinks[position])
						Log.d("Recirculation", "trackImpression: pos=$position")
					}
				}
			}
		}

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

				Spacer(modifier = Modifier.height(32.dp))
				Divider(color = Color.LightGray)
				Spacer(modifier = Modifier.height(16.dp))

				Text(
					text = "Recommended Articles",
					color = Color.Black,
					style = TextStyle.Default.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
					modifier = Modifier.padding(bottom = 12.dp)
				)

				articles.forEach { article ->
					ArticleCard(
						article = article,
						onLayoutMeasured = { contentTop ->
							cardContentOffsets[article.position] = contentTop
						},
						onClick = {
							recirculation.trackClick(MODULE_NAME, recirculationLinks[article.position])
							Log.d("Recirculation", "trackClick: pos=${article.position}")
						}
					)
				}
			}
		}
	}

	@Composable
	private fun ArticleCard(
		article: ArticleItem,
		onLayoutMeasured: (Float) -> Unit,
		onClick: () -> Unit,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(vertical = 6.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(Color(0xFFF5F5F5))
				.clickable { onClick() }
				.padding(16.dp)
				.onGloballyPositioned { coordinates ->
					onLayoutMeasured(coordinates.boundsInRoot().top)
				}
		) {
			Text(
				text = article.title,
				color = Color.Black,
				style = TextStyle.Default.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
			)
			Text(
				text = article.url,
				color = Color.Gray,
				style = TextStyle.Default.copy(fontSize = 12.sp),
				modifier = Modifier.padding(top = 4.dp)
			)
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
