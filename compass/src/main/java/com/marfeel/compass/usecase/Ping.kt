package com.marfeel.compass.usecase

import android.util.Log
import com.marfeel.compass.core.Page
import com.marfeel.compass.core.PingEmitterState
import com.marfeel.compass.core.PingRequest
import com.marfeel.compass.core.UseCase
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage
import timber.log.Timber

internal class Ping(
	private val api: ApiClient,
	private val memory: Memory,
	private val storage: Storage,
) : UseCase<PingEmitterState, Unit> {
	override fun invoke(pingEmitterState: PingEmitterState) {
		val pingRequest = PingRequest(
			accountId = memory.readAccountId() ?: "",
			sessionTimeStamp = memory.readSession().timeStamp,
			referralUrl = null, //TODO
			url = pingEmitterState.url,
			previousUrl = memory.readPreviousUrl() ?: "",
			pageId = memory.readPage()?.pageId ?: "",
			userId = storage.readUserId(),
			sessionId = memory.readSession().id,
			pingCounter = pingEmitterState.pingCounter,
			currentTimeStamp = System.currentTimeMillis(),
			userType = storage.readUserType(),
			registeredUserId = storage.readUserId(),
			cookiesAllowed = true, //TODO
			scrollPercent = pingEmitterState.scrollPercent ?: 0,
			firsVisitTimeStamp = storage.readFirstSessionTimeStamp(),
			previousSessionTimeStamp = storage.readPreviousSessionTimeStamp(),
			timeOnPage = memory.readPage()?.timeOnPage(System.currentTimeMillis()) ?: 0L,
			pageStartTimeStamp = memory.readPage()?.startTimeStamp ?: 0L
		)
		api.ping(pingRequest)

		Log.d("xtest", "ping \n scrollPercentage: ${pingEmitterState?.scrollPercent} \n")
		Timber.d("Timber ping")
	}

}

private fun Page.timeOnPage(currentTimeMillis: Long): Long =
	(currentTimeMillis - startTimeStamp) / 1000

