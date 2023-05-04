package com.marfeel.compass.memory

import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.storage.Storage
import java.util.*

internal class Memory(private val storage: Storage) {

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
		Session(UUID.randomUUID().toString(), currentTimeStampInSeconds())

	fun updateSession(newSession: Session = newSession()) {
		storage.readLastPingTimeStamp()?.let {
			if (newSession.timeStamp > it) {
				storage.updatePreviousSessionLastPingTimeStamp(it)
			}
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

	fun clearTrackedConversions(conversions: List<String>) {
		pendingConversions.removeAll(conversions)
	}
}
