package com.marfeel.compass.storage

import com.marfeel.compass.core.model.compass.ConversionOptions
import com.marfeel.compass.core.model.compass.ConversionScope
import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal data class Conversion(
	val name: String,
	val options: ConversionOptions? = null,
	/** Resolved when tracked: `options.id`, or the id its scope stands for. */
	val id: String? = null,
	/** The page it was tracked on, so that a later page sending it does not get the credit. */
	val pageId: String? = null
)

/** A pending conversion a ping is sending; settle it by [key] once the ping returns. */
internal class ClaimedConversion(val key: Long, val conversion: Conversion)

/**
 * A conversion waiting to be sent. It leaves the queue only once a ping carrying it reaches
 * the server; [sending] keeps a second ping from picking it up meanwhile.
 */
private class PendingConversion(val key: Long, val conversion: Conversion) {
	var sending = false
}

internal class SessionStorage(private val storage: Storage) {

	private var accountId: String? = null
	private var page: Page? = null
	private var previousUrl: String? = null
	private val conversionsLock = Any()
	private val pendingConversions = mutableListOf<PendingConversion>()
	private var nextConversionKey = 0L
	private var pageVars: MutableMap<String, String> = ConcurrentHashMap()
	private var pageMetrics: MutableMap<String, Int> = ConcurrentHashMap()
	private var pageTechnology: Int? = null
	private var recirculationSource: String? = null
	private var cdpEnabled: Boolean = false

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
		enqueueConversion(Conversion(conversion, pageId = page?.pageId))
	}

	/**
	 * Queues the conversion on the current page unless one with the same name and id was
	 * already sent or is waiting to be. It is only marked as tracked once it is sent.
	 */
	fun addPendingConversion(conversion: String, options: ConversionOptions): Boolean =
		enqueueConversion(Conversion(conversion, options, conversionId(options), page?.pageId))

	private fun conversionId(options: ConversionOptions): String? =
		options.id ?: when (options.scope) {
			ConversionScope.User -> storage.readOriginalUserId()
			ConversionScope.Session -> readSession().id
			ConversionScope.Page -> page?.pageId
			null -> null
		}

	private fun conversionKey(name: String, id: String) = "$name:$id"

	private fun enqueueConversion(conversion: Conversion): Boolean = synchronized(conversionsLock) {
		conversion.id?.let { id ->
			val isPending = pendingConversions.any { it.conversion.name == conversion.name && it.conversion.id == id }
			if (isPending || conversionKey(conversion.name, id) in storage.readTrackedConversions()) {
				return false
			}
		}

		pendingConversions.add(PendingConversion(nextConversionKey++, conversion))
		true
	}

	fun readPendingConversions(): List<Conversion> = synchronized(conversionsLock) {
		pendingConversions.map { it.conversion }
	}

	fun hasPendingConversions(pageId: String): Boolean = synchronized(conversionsLock) {
		pendingConversions.any { it.conversion.pageId == pageId }
	}

	/** The pending conversions no other ping is sending, now marked as being sent. */
	fun claimPendingConversions(): List<ClaimedConversion> = synchronized(conversionsLock) {
		pendingConversions.filter { !it.sending }.map {
			it.sending = true
			ClaimedConversion(it.key, it.conversion)
		}
	}

	/** A sent conversion leaves the queue and is marked as tracked; an unsent one is retried by the next ping. */
	fun settleConversion(key: Long, sent: Boolean) {
		synchronized(conversionsLock) {
			val index = pendingConversions.indexOfFirst { it.key == key }
			if (index < 0) return

			if (!sent) {
				pendingConversions[index].sending = false
				return
			}

			val conversion = pendingConversions.removeAt(index).conversion
			conversion.id?.let { storage.addTrackedConversion(conversionKey(conversion.name, it)) }
		}
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

	fun setCdpEnabled(enabled: Boolean) {
		cdpEnabled = enabled
	}

	fun readCdpEnabled(): Boolean =
		cdpEnabled
}
