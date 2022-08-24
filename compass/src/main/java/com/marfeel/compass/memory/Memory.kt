package com.marfeel.compass.memory

internal class Memory {
	private var accountId: String? = null

	fun updateAccountId(id: String) {
		accountId = id
	}

	fun readAccountId(): String? =
		accountId
}
