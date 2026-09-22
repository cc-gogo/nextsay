package app.nextsay

import android.app.Application
import android.content.Context
import app.nextsay.diagnostics.LocalCrashHandler

class NextSayApplication : Application() {
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
        LocalCrashHandler.install(
            recorder = dependencies.diagnostics,
            eventFactory = dependencies.diagnosticEventFactory,
        )
    }
}

val Context.nextSayDependencies: AppDependencies
    get() = (applicationContext as NextSayApplication).dependencies
