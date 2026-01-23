package com.marfeel.compass.di

import android.annotation.SuppressLint
import android.content.Context
import com.marfeel.compass.core.ping.IngestPingEmitter
import com.marfeel.compass.core.ping.MultimediaPingEmitter
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.usecase.GetRFV
import com.marfeel.compass.usecase.IngestPing
import com.marfeel.compass.usecase.MultimediaPing
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import com.marfeel.compass.BuildConfig

@SuppressLint("StaticFieldLeak")
internal object CompassComponent : CompassServiceLocator {
    internal var context: Context? = null //It'll never leak since the only setter call ensure the context passed is the application one
    override val ingestPingEmitter: IngestPingEmitter by lazy { IngestPingEmitter(getPing()) }
    override val multimediaPingEmitter: MultimediaPingEmitter by lazy { MultimediaPingEmitter(getPingMultimedia()) }
    override val storage: Storage by lazy {
        val context = this.context
        checkNotNull(context)
        Storage(context)
    }
    override val apiClient: ApiClient by lazy {
        ApiClient(
            OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request()
                            .newBuilder()
                            .header("User-Agent", getUserAgent())
                            .build()
                    )
                }
                .build()
        )
    }

    private fun getDeviceType(): String {
        val context = this.context

        checkNotNull(context)
        return if (context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "mobile"
    }

    private fun getUserAgent(): String {
        val deviceType = getDeviceType()

        return "Marfeel-Android-SDK/${BuildConfig.VERSION} (Android) $deviceType"
    }

    override val sessionStorage: SessionStorage by lazy { SessionStorage(storage) }

    override fun getPing(): IngestPing = IngestPing(apiClient, sessionStorage, storage)

    override fun getRFV(): GetRFV = GetRFV(storage, sessionStorage, apiClient)

    override fun getPingMultimedia(): MultimediaPing = MultimediaPing(apiClient, sessionStorage, storage)
}

internal interface CompassServiceLocator {
    val ingestPingEmitter: IngestPingEmitter
    val multimediaPingEmitter: MultimediaPingEmitter
    val storage: Storage
    val apiClient: ApiClient
    val sessionStorage: SessionStorage
    fun getPing(): IngestPing
    fun getRFV(): GetRFV
    fun getPingMultimedia(): MultimediaPing
}
