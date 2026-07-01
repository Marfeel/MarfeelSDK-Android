package com.marfeel.compass.core.ping

import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IngestPingEmitterStateTest {

	private val clockClass = "com.marfeel.compass.core.model.compass.CompassKt"

	@Before
	fun setUp() {
		mockkStatic(clockClass)
	}

	@After
	fun tearDown() {
		unmockkStatic(clockClass)
	}

	private fun stateStartedAtSecond(second: Long, timeOnBackground: Long = 0): IngestPingEmitterState {
		every { currentTimeStampInSeconds() } returns second
		return IngestPingEmitterState(
			url = "url",
			pageId = "pid",
			sessionId = "sid",
			scrollPercent = null,
			pageStartTimeStamp = currentTimeStampInSeconds(),
			timeOnBackground = timeOnBackground,
		)
	}

	@Test
	fun `activeTimeOnPage advances on each read without a state copy`() {
		// Regression: it used to be a stored val frozen at construction, so `l` stopped
		// counting once the reader stopped scrolling (e.g. at the article end).
		val state = stateStartedAtSecond(1000)

		every { currentTimeStampInSeconds() } returns 1030
		assertEquals(30L, state.activeTimeOnPage)

		// Same instance, no scroll / no copy — must keep advancing.
		every { currentTimeStampInSeconds() } returns 1060
		assertEquals(60L, state.activeTimeOnPage)
	}

	@Test
	fun `activeTimeOnPage excludes time spent on background`() {
		val state = stateStartedAtSecond(1000, timeOnBackground = 20)

		every { currentTimeStampInSeconds() } returns 1060
		// 60s elapsed − 20s background = 40s.
		assertEquals(40L, state.activeTimeOnPage)
	}
}
