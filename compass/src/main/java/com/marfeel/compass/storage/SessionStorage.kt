package com.marfeel.compass.storage

import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import java.util.*

internal class SessionStorage(private val storage: Storage) {

	private var accountId: String? = null
	private var page: Page? = null
	private var previousUrl: String? = null
	private var pendingConversions: MutableList<String> = mutableListOf()
	private var pageVars: MutableMap<String, String> = mutableMapOf()
	private var pageTechnology: Int? = null
	private var landingPage: String? = null

	fun updateAccountId(id: String) {
		accountId = id
	}

	fun readAccountId(): String? =
		accountId

	fun readSession(): Session =
		storage.readSession() ?: newSession().also {
			storage.setSession(it)
		}

	private fun newSession(): Session =
		Session(UUID.randomUUID().toString(), currentTimeStampInSeconds())

	fun updateSession(newSession: Session = newSession()) {
		storage.readLastPingTimeStamp()?.let {
			if (newSession.timeStamp > it) {
				storage.updatePreviousSessionLastPingTimeStamp(it)
			}
		}
		storage.setSession(newSession)
		storage.clearSessionVars()
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

	fun addPageVar(name: String, value: String) {
		pageVars[name] = value
	}

	fun readPageVars(): Map<String, String> {
		return pageVars.toMap()
	}

	fun clearPageVars() {
		pageVars.clear()
	}

	fun addSessionVar(name: String, value: String) {
		storage.setSessionVar(name, value)
	}

	fun readSessionVars(): Map<String, String> {
		return storage.readSessionVars()
	}

	fun clearSessionVars() {
		storage.clearSessionVars()
	}

	fun setPageTechnology(tech: Int) {
		pageTechnology = tech
	}

	fun readPageTechnology(): Int? =
		pageTechnology

	fun setLandingPage(lp: String) {
		landingPage = lp
	}

	fun readLandingPage(): String? =
		landingPage
}
