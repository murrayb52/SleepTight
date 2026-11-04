package io.github.murrayb52.sleeptight

import android.content.Context
import io.github.murrayb52.sleeptight.data.NightRepository
import io.github.murrayb52.sleeptight.data.SleepTightDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Manual dependency wiring: one instance per process, created by [SleepTightApplication]. */
class AppContainer(context: Context) {
    val clock: Clock = DeviceClock
    val database: SleepTightDatabase = SleepTightDatabase.create(context)
    val nightRepository = NightRepository(database, clock)
}

/**
 * The system clock in whatever zone the device is in *now*. Unlike `Clock.systemDefaultZone()`,
 * it follows time-zone changes made while the app is running.
 */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
