package com.marfeel.compass.tracker

import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.core.ping.IngestPingEmitter
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.usecase.IngestPing
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal class CompassTrackerSessionTest {

	private val storage = mockk<Storage>(relaxed = true)
	private val apiClient = mockk<ApiClient>(relaxed = true)
	private val sessionStorageInstance = SessionStorage(storage)
	private val ingestPing = IngestPing(apiClient, sessionStorageInstance, storage)
	private val pingEmitter = IngestPingEmitter(ingestPing)
	private val sessionHolder = AtomicReference<Session?>(null)

	@Before
	fun setup() {
		val stale = currentTimeStampInSeconds() - 40 * 60
		sessionHolder.set(Session(UUID.randomUUID().toString(), stale))

		every { storage.readSession() } answers { sessionHolder.get() }
		every { storage.setSession(any()) } answers {
			sessionHolder.set(firstArg())
		}
		every { storage.readLastPingTimeStamp() } returns stale

		mockkObject(CompassComponent)
		every { CompassComponent.storage } returns storage
		every { CompassComponent.sessionStorage } returns sessionStorageInstance
		every { CompassComponent.ingestPingEmitter } returns pingEmitter
	}

	@After
	fun tearDown() {
		unmockkObject(CompassComponent)
	}

	@Test
	fun `onResume right after initialize does not rotate session a second time`() {
		CompassTracker.initialize("acct-id", 4)
		val sessionAfterInit = sessionHolder.get()!!.id

		pingEmitter.onResumeCallback.invoke()
		val sessionAfterResume = sessionHolder.get()!!.id

		assertEquals(sessionAfterInit, sessionAfterResume)
	}
}
