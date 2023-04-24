package com.marfeel.compass.usecase

import com.marfeel.compass.core.NoInputUseCase
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.RfvPayloadData
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal class GetRFV(
	private val storage: Storage,
	private val memory: Memory,
	private val api: ApiClient
) : NoInputUseCase<RFV?> {
	override fun invoke(): RFV? {
		val request = RfvPayloadData(
			accountId = memory.readAccountId() ?: "",
			originalUserId = storage.readOriginalUserId(),
			registeredUserId = storage.readRegisteredUserId(),
			previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp()
			)
		return api.getRfv(request).getOrNull()
	}
}
