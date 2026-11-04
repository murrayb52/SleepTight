package io.github.murrayb52.sleeptight

import android.app.Application
import io.github.murrayb52.sleeptight.tracking.Notifications
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
        // Score any night that has no score yet (or one from an older scoring version)...
        container.appScope.launch { container.nightScorer.rescoreStale() }
        // ...and rescore everything when the goals behind the score change (not on the first read).
        container.appScope.launch {
            container.nightRepository.settings
                .map { Triple(it.targetBedtimeMinutes, it.targetSleepMinutes, it.maxSnoozes) }
                .distinctUntilChanged()
                .drop(1)
                .collect { container.nightScorer.rescoreAll() }
        }
    }
}
