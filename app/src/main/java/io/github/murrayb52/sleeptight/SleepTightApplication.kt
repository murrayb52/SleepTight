package io.github.murrayb52.sleeptight

import android.app.Application

class SleepTightApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
