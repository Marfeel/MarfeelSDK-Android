package com.marfeel.compass.di

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
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal object CompassComponent : CompassServiceLocator {
    var context: Context? = null
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
                .addNetworkInterceptor { chain ->
                    chain.proceed(
                        chain.request()
                            .newBuilder()
                            .header("User-Agent", "CompassAndroidSDK/${BuildConfig.VERSION}")
                            .build()
                    )
                }
                .addInterceptor(logging).build()
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
