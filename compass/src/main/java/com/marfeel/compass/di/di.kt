package com.marfeel.compass.di

import com.marfeel.compass.BackgroundWatcher
import com.marfeel.compass.core.PingEmitter
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private val compassKoinApplication = koinApplication {
    modules(compassModule)
}

private object CompassKoinContext {
    val koinApp: KoinApplication = compassKoinApplication
}

internal interface CompassKoinComponent : KoinComponent {
    override fun getKoin(): Koin = CompassKoinContext.koinApp.koin
}

private val compassModule = module {
    single { PingEmitter() }
    single { BackgroundWatcher(get()) }
}