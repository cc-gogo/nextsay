package app.nextsay

import android.app.Application
import android.content.Context

class NextSayApplication : Application() {
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
    }
}

val Context.nextSayDependencies: AppDependencies
    get() = (applicationContext as NextSayApplication).dependencies
