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
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import com.marfeel.compass.core.model.compass.ConversionOptions
import com.marfeel.compass.core.model.compass.ConversionScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.experiences.ExperiencesTracking
import com.marfeel.compass.experiences.RecirculationTracking
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.experiences.model.RecirculationModule
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

    tracker.trackScreen("main screen")
	tracker.setSessionVar("pepe", "pepa")
	tracker.setSessionVar("pepe2", "pepa2")
	tracker.setUserVar("lolo", "lola")
	tracker.setUserVar("lolo2", "lola2")
	tracker.addUserSegment("segment")
	tracker.addUserSegment("another-segment")
	tracker.setUserConsent(true)
	tracker.setUserType(UserType.Custom(11))
	tracker.setUserConsent(true)
	tracker.setPageMetric("metric_1", 1)
	tracker.setPageMetric("metric_2", 2)

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


			Column(
				modifier = Modifier.fillMaxWidth().padding(15.dp)
			) {
				var conversion by remember { mutableStateOf("") }
				var options by remember { mutableStateOf(ConversionOptions()) }

				TextField(
					modifier = Modifier.fillMaxWidth(),
					value = conversion,
					onValueChange = { conversion = it },
					label = { Text("Conversion name") }
				)
				FloatingActionButton(
					modifier = Modifier
						.align(Alignment.CenterHorizontally)
						.padding(top = 16.dp, start = 16.dp),
					backgroundColor = Color(0xB2222222),
					onClick = { tracker.trackConversion(conversion) }
				) {
					Text(text = "Track conversion", color = Color.White)
				}
				TextField(
					modifier = Modifier.fillMaxWidth(),
					value = options.id ?: "",
					onValueChange = { options = options.copy(id = it.ifEmpty { null }) },
					label = { Text("Id") }
				)
				TextField(
					modifier = Modifier.fillMaxWidth(),
					value = options.value ?: "",
					onValueChange = { options = options.copy(value = it.ifEmpty { null }) },
					label = { Text("Value") }
				)
				var scopeExpanded by remember { mutableStateOf(false) }
				Box {
					OutlinedButton(
						modifier = Modifier.fillMaxWidth(),
						onClick = { scopeExpanded = true }
					) {
						Text(text = options.scope?.name ?: "Select Scope")
					}
					DropdownMenu(
						expanded = scopeExpanded,
						onDismissRequest = { scopeExpanded = false }
					) {
						DropdownMenuItem(onClick = {
							options = options.copy(scope = null)
							scopeExpanded = false
						}) {
							Text("None")
						}
						ConversionScope.values().forEach { scope ->
							DropdownMenuItem(onClick = {
								options = options.copy(scope = scope)
								scopeExpanded = false
							}) {
								Text(scope.name)
							}
						}
					}
				}
				Text(
					text = "Meta",
					modifier = Modifier.padding(top = 16.dp),
					color = Color.Black
				)
				var metaKey by remember { mutableStateOf("") }
				var metaValue by remember { mutableStateOf("") }
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
					TextField(
						modifier = Modifier.weight(1f),
						value = metaKey,
						onValueChange = { metaKey = it },
						label = { Text("Key") }
					)
					Spacer(modifier = Modifier.width(8.dp))
					TextField(
						modifier = Modifier.weight(1f),
						value = metaValue,
						onValueChange = { metaValue = it },
						label = { Text("Value") }
					)
					IconButton(
						onClick = {
							if (metaKey.isNotEmpty()) {
								val currentMeta = options.meta?.toMutableMap() ?: mutableMapOf()
								currentMeta[metaKey] = metaValue
								options = options.copy(meta = currentMeta)
								metaKey = ""
								metaValue = ""
							}
						}
					) {
						Icon(Icons.Rounded.Add, contentDescription = "Add")
					}
				}
				options.meta?.forEach { (key, value) ->
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = "$key: $value",
							modifier = Modifier.weight(1f),
							color = Color.DarkGray
						)
						IconButton(
							onClick = {
								val currentMeta = options.meta?.toMutableMap() ?: mutableMapOf()
								currentMeta.remove(key)
								options = options.copy(meta = currentMeta.ifEmpty { null })
							}
						) {
							Icon(Icons.Rounded.Close, contentDescription = "Remove")
						}
					}
				}

				FloatingActionButton(
					modifier = Modifier
						.align(Alignment.CenterHorizontally)
						.padding(top = 16.dp, start = 16.dp),
					backgroundColor = Color(0xB2222222),
					onClick = { tracker.trackConversion(conversion, options) }
				) {
					Text(text = "Track conversion with options", color = Color.White)
				}

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

			Text(
				text = "Experiences",
				color = Color.Black,
				style = titleStyle,
				modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
			)

			var experiencesUrl by remember { mutableStateOf("https://dev.marfeel.co/") }
			var experiencesResult by remember { mutableStateOf("") }
			var experiencesLoading by remember { mutableStateOf(false) }
			var lastExperiences by remember { mutableStateOf<List<Experience>>(emptyList()) }
			var selectedType by remember { mutableStateOf<ExperienceType?>(null) }
			var typeExpanded by remember { mutableStateOf(false) }
			var experimentsVersion by remember { mutableStateOf(0) }
			val experiencesTracker = remember { ExperiencesTracking.getInstance() }
			val recirculationTracker = remember { RecirculationTracking.getInstance() }

			TextField(
				modifier = Modifier.fillMaxWidth(),
				value = experiencesUrl,
				onValueChange = { experiencesUrl = it },
				label = { Text("URL") }
			)

			Box(modifier = Modifier.padding(top = 8.dp)) {
				OutlinedButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = { typeExpanded = true }
				) {
					Text(text = selectedType?.name ?: "Filter by type: All")
				}
				DropdownMenu(
					expanded = typeExpanded,
					onDismissRequest = { typeExpanded = false }
				) {
					DropdownMenuItem(onClick = {
						selectedType = null
						typeExpanded = false
					}) {
						Text("All")
					}
					ExperienceType.values().forEach { type ->
						DropdownMenuItem(onClick = {
							selectedType = type
							typeExpanded = false
						}) {
							Text(type.name)
						}
					}
				}
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 16.dp),
				horizontalArrangement = Arrangement.SpaceEvenly
			) {
				FloatingActionButton(
					backgroundColor = Color(0xFF1231D1),
					onClick = {
						experiencesLoading = true
						experiencesResult = ""
						coroutineScope.launch {
							try {
								val experiences = experiencesTracker.fetchExperiences(
									url = experiencesUrl,
									filterByType = selectedType,
									resolve = false
								)
								lastExperiences = experiences
								experimentsVersion++
								experiencesResult = "Found ${experiences.size} experiences:\n" +
									experiences.joinToString("\n") { exp ->
										"- [${exp.typeRaw}] ${exp.name} (id=${exp.id})"
									}
							} catch (e: Exception) {
								experiencesResult = "Error: ${e.message}"
							}
							experiencesLoading = false
						}
					}
				) {
					Text(text = "Fetch", color = Color.White)
				}
				FloatingActionButton(
					backgroundColor = Color(0xFF641172),
					onClick = {
						experiencesLoading = true
						experiencesResult = ""
						coroutineScope.launch {
							try {
								val experiences = experiencesTracker.fetchExperiences(
									url = experiencesUrl,
									filterByType = selectedType,
									resolve = true
								)
								lastExperiences = experiences
								experimentsVersion++
								experiencesResult = "Found ${experiences.size} experiences:\n" +
									experiences.joinToString("\n") { exp ->
										val resolved = if (exp.resolvedContent != null)
											" [resolved: ${exp.resolvedContent!!.take(100)}...]"
										else ""
										"- [${exp.typeRaw}] ${exp.name}$resolved"
									}
							} catch (e: Exception) {
								experiencesResult = "Error: ${e.message}"
							}
							experiencesLoading = false
						}
					}
				) {
					Text(text = "Fetch + Resolve", color = Color.White)
				}
			}

			if (experiencesLoading) {
				Text(
					text = "Loading...",
					color = Color.Gray,
					modifier = Modifier.padding(top = 8.dp)
				)
			}

			if (experiencesResult.isNotEmpty()) {
				Text(
					text = experiencesResult,
					color = Color.Black,
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.clip(RoundedCornerShape(4.dp))
						.background(Color(0xFFF0F0F0))
						.padding(12.dp),
					style = TextStyle.Default.copy(fontSize = 12.sp)
				)
			}

			if (lastExperiences.isNotEmpty()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp),
					horizontalArrangement = Arrangement.SpaceEvenly
				) {
					FloatingActionButton(
						backgroundColor = Color(0xFF00AA00),
						onClick = {
							val experienceLinks = lastExperiences.associateWith { exp ->
								listOf(
									RecirculationLink(
										url = exp.contentUrl ?: "",
										position = "0"
									)
								)
							}
							experiencesTracker.trackElegible(experienceLinks)
						}
					) {
						Text(text = "Elegible", color = Color.White)
					}
					FloatingActionButton(
						backgroundColor = Color(0xFFAA6600),
						onClick = {
							lastExperiences.firstOrNull()?.let { exp ->
								val links = listOf(
									RecirculationLink(
										url = exp.contentUrl ?: "",
										position = "0"
									)
								)
								experiencesTracker.trackImpression(exp, links)
							}
						}
					) {
						Text(text = "Impression", color = Color.White)
					}
					FloatingActionButton(
						backgroundColor = Color(0xFFAA0000),
						onClick = {
							lastExperiences.firstOrNull()?.let { exp ->
								experiencesTracker.trackClick(
									exp,
									RecirculationLink(
										url = exp.contentUrl ?: "",
										position = "0"
									)
								)
							}
						}
					) {
						Text(text = "Click", color = Color.White)
					}
				}

				var capsVersion by remember { mutableStateOf(0) }
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 32.dp, bottom = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Text(
						text = "Frequency Caps",
						color = Color.Black,
						style = titleStyle
					)
					OutlinedButton(onClick = {
						experiencesTracker.clearFrequencyCaps()
						capsVersion++
					}) {
						Text(text = "Clear", style = TextStyle.Default.copy(fontSize = 12.sp))
					}
				}
				Text(
					text = "Only experiences declared in the response's targeting.frequencyCap are listed. Tap Impression/Close, then re-Fetch and inspect the `uexp` query param.",
					color = Color.Gray,
					modifier = Modifier.padding(bottom = 8.dp),
					style = TextStyle.Default.copy(fontSize = 12.sp)
				)
				val capConfig = remember(capsVersion, lastExperiences) {
					experiencesTracker.getFrequencyCapConfig()
				}
				val cappedExperiences = lastExperiences.filter { it.id in capConfig.keys }
				if (cappedExperiences.isEmpty()) {
					Text(
						text = "No experiences capped in the current response.",
						color = Color.Gray,
						style = TextStyle.Default.copy(fontSize = 12.sp)
					)
				}
				cappedExperiences.forEach { exp ->
					val counts = remember(capsVersion, exp.id) {
						experiencesTracker.getFrequencyCapCounts(exp.id)
					}
					Column(modifier = Modifier.padding(vertical = 4.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically
						) {
							val capKeys = capConfig[exp.id].orEmpty().joinToString(",")
							Text(
								text = "${exp.typeRaw}/${exp.id.take(16)}… [$capKeys]",
								color = Color.Black,
								modifier = Modifier.weight(1f),
								style = TextStyle.Default.copy(fontSize = 11.sp)
							)
							OutlinedButton(onClick = {
								experiencesTracker.trackImpression(exp)
								capsVersion++
							}) {
								Text(text = "Impression", style = TextStyle.Default.copy(fontSize = 11.sp))
							}
							Spacer(modifier = Modifier.width(4.dp))
							OutlinedButton(onClick = {
								experiencesTracker.trackClose(exp)
								capsVersion++
							}) {
								Text(text = "Close", style = TextStyle.Default.copy(fontSize = 11.sp))
							}
						}
						Text(
							text = "l=${counts["l"]} cl=${counts["cl"]} m=${counts["m"]} cm=${counts["cm"]} w=${counts["w"]} cw=${counts["cw"]} d=${counts["d"]} cd=${counts["cd"]} ls=${counts["ls"]}",
							color = Color.Gray,
							style = TextStyle.Default.copy(fontSize = 10.sp)
						)
					}
				}
			}

			Text(
				text = "Experiments",
				color = Color.Black,
				style = titleStyle,
				modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
			)
			Text(
				text = "Draws happen during Fetch. Force a variant to pin a branch; Clear to re-roll on next Fetch. Targeting is sent as trg=experiment::groupId=variantId.",
				color = Color.Gray,
				modifier = Modifier.padding(bottom = 8.dp),
				style = TextStyle.Default.copy(fontSize = 12.sp)
			)

			val experimentAssignments = remember(experimentsVersion) {
				experiencesTracker.getExperimentAssignments()
			}

			if (experimentAssignments.isEmpty()) {
				Text(
					text = "No assignments yet. Fetch experiences to draw, or force one below.",
					color = Color.Gray,
					style = TextStyle.Default.copy(fontSize = 12.sp)
				)
			} else {
				experimentAssignments.forEach { (groupId, variantId) ->
					Text(
						text = "$groupId → $variantId",
						color = Color.Black,
						modifier = Modifier.padding(vertical = 2.dp),
						style = TextStyle.Default.copy(fontSize = 11.sp)
					)
				}
			}

			var forceGroupId by remember { mutableStateOf("") }
			var forceVariantId by remember { mutableStateOf("") }
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				TextField(
					modifier = Modifier.weight(1f),
					value = forceGroupId,
					onValueChange = { forceGroupId = it },
					label = { Text("groupId") }
				)
				Spacer(modifier = Modifier.width(8.dp))
				TextField(
					modifier = Modifier.weight(1f),
					value = forceVariantId,
					onValueChange = { forceVariantId = it },
					label = { Text("variantId") }
				)
			}
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp),
				horizontalArrangement = Arrangement.SpaceEvenly
			) {
				OutlinedButton(onClick = {
					if (forceGroupId.isNotBlank() && forceVariantId.isNotBlank()) {
						experiencesTracker.setExperimentAssignment(forceGroupId.trim(), forceVariantId.trim())
						forceGroupId = ""
						forceVariantId = ""
						experimentsVersion++
					}
				}) {
					Text(text = "Force variant")
				}
				OutlinedButton(onClick = {
					experiencesTracker.clearExperimentAssignments()
					experimentsVersion++
				}) {
					Text(text = "Clear assignments")
				}
			}

			Text(
				text = "Generic Recirculation",
				color = Color.Black,
				style = titleStyle,
				modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
			)

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp),
				horizontalArrangement = Arrangement.SpaceEvenly
			) {
				FloatingActionButton(
					backgroundColor = Color(0xFF00AA00),
					onClick = {
						recirculationTracker.trackElegible(
							listOf(
								RecirculationModule(
									name = "demo-module",
									links = listOf(
										RecirculationLink(url = "https://example.com/1", position = "0"),
										RecirculationLink(url = "https://example.com/2", position = "1")
									)
								)
							)
						)
					}
				) {
					Text(text = "Elegible", color = Color.White)
				}
				FloatingActionButton(
					backgroundColor = Color(0xFFAA6600),
					onClick = {
						recirculationTracker.trackImpression(
							RecirculationModule(
								name = "demo-module",
								links = listOf(
									RecirculationLink(url = "https://example.com/1", position = "0")
								)
							)
						)
					}
				) {
					Text(text = "Impression", color = Color.White)
				}
				FloatingActionButton(
					backgroundColor = Color(0xFFAA0000),
					onClick = {
						recirculationTracker.trackClick(
							RecirculationModule(
								name = "demo-module",
								links = listOf(
									RecirculationLink(url = "https://example.com/1", position = "0")
								)
							)
						)
					}
				) {
					Text(text = "Click", color = Color.White)
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
