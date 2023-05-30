package com.marfeel.compass.usecase

import androidx.annotation.WorkerThread
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.UseCase
import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage

internal abstract class Ping<T, Y: PingData>(open val api: ApiClient, open val memory: Memory, open val storage: Storage) : UseCase<T, Unit> {
    override fun invoke(input: T) {
        getData(input)?.let {
            invoke(it)
        }
    }

    @WorkerThread
    abstract operator fun invoke(input: Y)

    abstract fun getData(input: T): Y?

    fun getData(): PingData? {
        val currentTimeStamp = currentTimeStampInSeconds()
        val currentSession = memory.readSession()
        val page = memory.readPage() ?: return null

        return PingData(
            accountId = memory.readAccountId() ?: "",
            sessionTimeStamp = currentSession.timeStamp,
            url = page.url,
            canonicalUrl = page.url,
            previousUrl = memory.readPreviousUrl() ?: "",
            pageId = memory.readPage()?.pageId ?: "",
            originalUserId = storage.readOriginalUserId(),
            sessionId = currentSession.id,
            currentTimeStamp = currentTimeStamp,
            userType = storage.readUserType(),
            registeredUserId = storage.readRegisteredUserId() ?: "",
            firsVisitTimeStamp = storage.readFirstSessionTimeStamp(),
            previousSessionTimeStamp = storage.readPreviousSessionLastPingTimeStamp(),
            version = BuildConfig.API_VERSION,
            userVars = storage.readUserVars(),
            pageVars = memory.readPageVars(),
            sessionVars = memory.readSessionVars(),
            userSegments = storage.readUserSegments()
        )
    }
}

