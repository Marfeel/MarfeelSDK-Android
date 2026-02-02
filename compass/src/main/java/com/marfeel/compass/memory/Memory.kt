package com.marfeel.compass.memory

import com.marfeel.compass.core.ConversionOptions
import com.marfeel.compass.core.Page
import com.marfeel.compass.core.Session
import com.marfeel.compass.core.currentTimeStampInSeconds
import com.marfeel.compass.di.CompassKoinComponent
import com.marfeel.compass.storage.Storage
import java.util.*

internal data class Conversion(
	val name: String,
	val options: ConversionOptions? = null
)

internal class Memory(private val storage: Storage) : CompassKoinComponent {

	private var accountId: String? = null
	private var session: Session? = null
	private var page: Page? = null
	private var previousUrl: String? = null
	private var pendingConversions: MutableList<Conversion> = mutableListOf()
	private val trackedConversionIds: MutableSet<String> = mutableSetOf()

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
		pendingConversions.add(Conversion(conversion))
	}

	fun addPendingConversion(conversion: String, options: ConversionOptions) {
		val conversionId = options.id
		if (conversionId != null) {
			val key = "$conversion:$conversionId"
			if (trackedConversionIds.contains(key)) {
				return
			}
			trackedConversionIds.add(key)
		}
		pendingConversions.add(Conversion(conversion, options))
	}

	fun readPendingConversions(): List<Conversion> =
		pendingConversions.toList()

	fun clearTrackedConversions(conversions: List<Conversion>) {
		pendingConversions.removeAll(conversions)
	}
}

