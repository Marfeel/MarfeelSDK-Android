package com.marfeel.compass.usecase

import com.marfeel.compass.core.NoInputUseCase
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.RfvPayloadData
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

private const val RFV_TTL = 30 * 1000;

internal class GetRFV(
	private val storage: Storage,
	private val memory: Memory,
	private val api: ApiClient
) : NoInputUseCase<RFV?> {
	private var rfvTs: Long? = null;
	private var cachedRFV: RFV? = null;

	override fun invoke(): RFV? {
		val currentTimeInMs = System.currentTimeMillis()

		if (rfvTs == null || (rfvTs!! + RFV_TTL) < currentTimeInMs) {
			val request = RfvPayloadData(
				accountId = memory.readAccountId() ?: "",
				originalUserId = storage.readOriginalUserId(),
				registeredUserId = storage.readRegisteredUserId(),
				previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp()
			)
			cachedRFV = api.getRfv(request).getOrNull()
			rfvTs = currentTimeInMs
		}

		return cachedRFV
	}
}
