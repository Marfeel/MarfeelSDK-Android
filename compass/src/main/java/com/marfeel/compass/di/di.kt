package com.marfeel.compass.di

import android.content.Context
import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.core.PingEmitter
import com.marfeel.compass.memory.Memory
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.usecase.GetRFV
import com.marfeel.compass.usecase.Ping
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private val compassModule = module {
	single { PingEmitter(get()) }
	single { BackgroundWatcher(get()) }
	single { Storage(androidContext(), Dispatchers.IO) }
	single { ApiClient(OkHttpClient()) }
	single { Memory() }
	factory { Ping(get(), get(), get()) }
	factory { GetRFV(get(), get(), get()) }
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
