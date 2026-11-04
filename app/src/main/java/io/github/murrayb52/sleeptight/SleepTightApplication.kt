package io.github.murrayb52.sleeptight

import android.app.Application
import io.github.murrayb52.sleeptight.tracking.Notifications

class SleepTightApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
