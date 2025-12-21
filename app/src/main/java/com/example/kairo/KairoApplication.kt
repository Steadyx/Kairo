package com.example.kairo

import android.app.Application
import com.example.kairo.core.dispatchers.DefaultDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KairoApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val dispatcherProvider = DefaultDispatcherProvider()
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, dispatcherProvider)
        applicationScope.launch { container.sampleSeeder.seedIfEmpty() }
    }
}
