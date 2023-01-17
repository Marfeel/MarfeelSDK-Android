package com.marfeel.compass.di

import android.annotation.SuppressLint
import android.content.Context
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.core.PingEmitter
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.usecase.GetRFV
import com.marfeel.compass.usecase.Ping
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor

@SuppressLint("StaticFieldLeak")
internal object CompassComponent : CompassServiceLocator {
    internal var context: Context? = null //It'll never leak since the only setter call ensure the context passed is the application one
    override val pingEmitter: PingEmitter by lazy { PingEmitter(getPing()) }
    override val storage: Storage by lazy {
        val context = this.context
        checkNotNull(context)
        Storage(context, Dispatchers.IO)
    }
    override val apiClient: ApiClient by lazy {
        val logging = HttpLoggingInterceptor()
        logging.level = (HttpLoggingInterceptor.Level.BODY)
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
                .addInterceptor(logging)
                .build()
        )
    }
    override val memory: Memory by lazy { Memory(storage) }

    override fun getPing(): Ping = Ping(apiClient, memory, storage)

    override fun getRFV(): GetRFV = GetRFV(storage, memory, apiClient)
}

internal interface CompassServiceLocator {
    val pingEmitter: PingEmitter
    val storage: Storage
    val apiClient: ApiClient
    val memory: Memory
    fun getPing(): Ping
    fun getRFV(): GetRFV
}
