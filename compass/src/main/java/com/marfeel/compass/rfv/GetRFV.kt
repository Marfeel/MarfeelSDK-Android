package com.marfeel.compass.rfv

import com.marfeel.compass.core.NoInputUseCase
import com.marfeel.compass.core.RfvRequest
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal class GetRFV(
	private val storage: Storage,
	private val api: ApiClient
) : NoInputUseCase<String?> {
	override fun invoke(): String? {
		val request = RfvRequest(
			accountId = storage.readAccountId() ?: "",
			userId = storage.readUserId()
		)
		return api.getRfv(request).getOrNull()
	}
}
