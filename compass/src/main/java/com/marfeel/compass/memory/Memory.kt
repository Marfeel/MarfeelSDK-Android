package com.marfeel.compass.memory

import com.marfeel.compass.core.Session
import java.util.UUID

internal class Memory {

	private var accountId: String? = null
	private var session: Session? = null

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
		session = newSession
	}

}
