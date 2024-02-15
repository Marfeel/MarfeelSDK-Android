package com.marfeel.compass.di

import android.annotation.SuppressLint
import android.content.Context
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.ping.IngestPingEmitter
import com.marfeel.compass.core.ping.MultimediaPingEmitter
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.usecase.GetRFV
import com.marfeel.compass.usecase.IngestPing
import com.marfeel.compass.usecase.MultimediaPing
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Protocol

@SuppressLint("StaticFieldLeak")
internal object CompassComponent : CompassServiceLocator {
    internal var context: Context? = null //It'll never leak since the only setter call ensure the context passed is the application one
    override val ingestPingEmitter: IngestPingEmitter by lazy { IngestPingEmitter(getPing()) }
    override val multimediaPingEmitter: MultimediaPingEmitter by lazy { MultimediaPingEmitter(getPingMultimedia()) }
    override val storage: Storage by lazy {
        val context = this.context
        checkNotNull(context)
        Storage(context, Dispatchers.IO)
    }
    override val apiClient: ApiClient by lazy {
        ApiClient(
            OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .addNetworkInterceptor { chain ->
                    chain.proceed(
                        chain.request()
                            .newBuilder()
                            .header("User-Agent", "CompassAndroidSDK/${BuildConfig.VERSION}")
                            .build()
                    )
                }
                .build()
        )
    }
    override val memory: Memory by lazy { Memory(storage) }

    override fun getPing(): IngestPing = IngestPing(apiClient, memory, storage)

    override fun getRFV(): GetRFV = GetRFV(storage, memory, apiClient)

    override fun getPingMultimedia(): MultimediaPing = MultimediaPing(apiClient, memory, storage)
}

internal interface CompassServiceLocator {
    val ingestPingEmitter: IngestPingEmitter
    val multimediaPingEmitter: MultimediaPingEmitter
    val storage: Storage
    val apiClient: ApiClient
    val memory: Memory
    fun getPing(): IngestPing
    fun getRFV(): GetRFV
    fun getPingMultimedia(): MultimediaPing
}
