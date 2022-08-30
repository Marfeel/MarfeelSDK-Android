package com.marfeel.compass.memory

import com.marfeel.compass.core.Page
import com.marfeel.compass.core.Session
import com.marfeel.compass.di.CompassKoinComponent
import com.marfeel.compass.storage.Storage
import org.koin.core.component.inject
import java.util.*

internal class Memory(private val storage: Storage) : CompassKoinComponent {

	private var accountId: String? = null
	private var session: Session? = null
	private var page: Page? = null
	private var previousUrl: String? = null
	private var pendingConversions: MutableList<String> = mutableListOf()

	fun updateAccountId(id: String) {
		accountId = id
	}

	fun readAccountId(): String? =
		accountId

	fun readSession(): Session =
		session ?: newSession().also { session = it }

	private fun newSession(): Session =
		Session(UUID.randomUUID().toString(), System.currentTimeMillis())

	fun updateSession(newSession: Session) {
		val currentSession = session
		currentSession?.run {
			storage.updatePreviousSessionTimeStamp(timeStamp)
		}
		session = newSession
	}

	fun readPage(): Page? = page

	fun updatePage(newPage: Page) {
		page?.run { updatePreviousUrl(url) }
		page = newPage
	}

	fun readPreviousUrl(): String? = previousUrl

	private fun updatePreviousUrl(url: String) {
		previousUrl = url
	}

	fun addPendingConversion(conversion: String) {
		pendingConversions.add(conversion)
	}

	fun readPendingConversions(): List<String> =
		pendingConversions.toList()

	fun clearPendingConversions() {
		pendingConversions.clear()
	}
}
