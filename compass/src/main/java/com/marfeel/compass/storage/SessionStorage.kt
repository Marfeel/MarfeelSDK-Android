package com.marfeel.compass.storage

import com.marfeel.compass.core.model.compass.ConversionOptions
import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import java.util.*

internal data class Conversion(
	val name: String,
	val options: ConversionOptions? = null
)

internal class SessionStorage(private val storage: Storage) {

	private var accountId: String? = null
	private var page: Page? = null
	private var previousUrl: String? = null
	private var pendingConversions: MutableList<Conversion> = mutableListOf()
	private val trackedConversionIds: MutableSet<String> = mutableSetOf()
	private var pageVars: MutableMap<String, String> = mutableMapOf()
	private var pageMetrics: MutableMap<String, Int> = mutableMapOf()
	private var pageTechnology: Int? = null
	private var recirculationSource: String? = null

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
		storage.setLandingPage(null)
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
		pendingConversions.add(Conversion(conversion))
	}

	private fun shouldAddConversion(conversion: String, id: String?): Boolean {
		if (id == null) {
			return true
		}

		val key = "$conversion:$id"

		return !trackedConversionIds.contains(key)
	}

	fun addPendingConversion(conversion: String, options: ConversionOptions) {
		val conversionId = options.id

		if (shouldAddConversion(conversion, conversionId)) {
			if (conversionId != null) {
				val key = "$conversion:$conversionId"
				trackedConversionIds.add(key)
			}
			pendingConversions.add(Conversion(conversion, options))
		}
	}

	fun readPendingConversions(): List<Conversion> =
		pendingConversions.toList()

	fun clearTrackedConversions(conversions: List<Conversion>) {
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

	fun addPageMetric(name: String, value: Int) {
		pageMetrics[name] = value
	}

	fun readPageMetrics(): Map<String, Int> {
		return pageMetrics.toMap()
	}

	fun clearPageMetrics() {
		pageMetrics.clear()
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

	fun setLandingPage(lp: String? = null) {
		storage.setLandingPage(lp)
	}

	fun readLandingPage(): String? =
		storage.readLadingPage()

	fun updateRecirculationSource(rs: String? = null) {
		recirculationSource = rs
	}

	fun readRecirculationSource(): String? =
		recirculationSource
}
