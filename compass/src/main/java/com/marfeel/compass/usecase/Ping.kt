package com.marfeel.compass.usecase

import androidx.annotation.WorkerThread
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.UseCase
import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal abstract class Ping<T, Y: PingData>(open val api: ApiClient, open val sessionStorage: SessionStorage, open val storage: Storage) : UseCase<T, Unit> {
    override fun invoke(input: T) {
        getData(input)?.let {
            invoke(it)
        }
    }

    @WorkerThread
    abstract operator fun invoke(input: Y)

    abstract fun getData(input: T): Y?

    /**
     * [userVars] / [userSegments] default to the raw device-owned stores; `IngestPing`
     * passes its merged views instead so the beacon does not read and parse them twice.
     */
    fun getData(
        userVars: Map<String, String> = storage.readUserVars(),
        userSegments: List<String> = storage.readUserSegments()
    ): PingData? {
        val currentTimeStamp = currentTimeStampInSeconds()
        val currentSession = sessionStorage.readSession()
        val page = sessionStorage.readPage() ?: return null

        return PingData(
            accountId = sessionStorage.readAccountId() ?: "",
            sessionTimeStamp = currentSession.timeStamp,
            url = page.url,
            canonicalUrl = page.url,
            previousUrl = sessionStorage.readPreviousUrl() ?: "",
            pageId = sessionStorage.readPage()?.pageId ?: "",
            originalUserId = storage.readOriginalUserId(),
            sessionId = currentSession.id,
            currentTimeStamp = currentTimeStamp,
            userType = storage.readUserType(),
            registeredUserId = storage.readRegisteredUserId() ?: "",
            firsVisitTimeStamp = storage.readFirstSessionTimeStamp(),
            previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
            version = BuildConfig.API_VERSION,
            userVars = userVars,
            pageVars = sessionStorage.readPageVars(),
            sessionVars = sessionStorage.readSessionVars(),
            userSegments = userSegments,
            pageType = sessionStorage.readPageTechnology()!!,
            userConsent = storage.readUserConsent()
        )
    }
}

