package io.github.murrayb52.sleeptight

import android.app.Application
import io.github.murrayb52.sleeptight.tracking.Notifications
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SleepTightApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Keep the bedtime reminder in step with the settings, now and whenever they change.
        container.appScope.launch {
            container.nightRepository.settings
                .distinctUntilChanged { a, b ->
                    a.targetBedtimeMinutes == b.targetBedtimeMinutes && a.bedtimeReminderEnabled == b.bedtimeReminderEnabled
                }
                .collect { container.bedtimeReminders.schedule(it) }
        }
    }
}
