package com.marfeel.compass.di

import android.content.Context
import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.core.PingEmitter
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private val compassModule = module {
    single { PingEmitter() }
    single { BackgroundWatcher(get()) }
    single { Storage(androidContext(), Dispatchers.IO) }
    single { ApiClient(OkHttpClient()) }
}

private val compassKoinApplication = koinApplication {
    modules(compassModule)
}

private object CompassKoinContext {
    val koinApp: KoinApplication = compassKoinApplication
}

internal fun addAndroidContextToDiApplication(context: Context) {
    compassKoinApplication.androidContext(context.applicationContext)
}

internal interface CompassKoinComponent : KoinComponent {
    override fun getKoin(): Koin = CompassKoinContext.koinApp.koin
}
