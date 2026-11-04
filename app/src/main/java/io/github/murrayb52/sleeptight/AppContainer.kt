package io.github.murrayb52.sleeptight

import android.content.Context
import io.github.murrayb52.sleeptight.alarm.AlarmController
import io.github.murrayb52.sleeptight.alarm.AlarmEvents
import io.github.murrayb52.sleeptight.alarm.AlarmScheduler
import io.github.murrayb52.sleeptight.alarm.AlarmState
import io.github.murrayb52.sleeptight.alarm.BedtimeReminderScheduler
import io.github.murrayb52.sleeptight.data.NightRepository
import io.github.murrayb52.sleeptight.health.GarminImporter
import io.github.murrayb52.sleeptight.health.GarminSource
import io.github.murrayb52.sleeptight.health.HealthConnectGarminSource
import io.github.murrayb52.sleeptight.scoring.NightScorer
import io.github.murrayb52.sleeptight.data.RoomRecordingStore
import io.github.murrayb52.sleeptight.data.SleepTightDatabase
import io.github.murrayb52.sleeptight.tracking.NightTracker
import io.github.murrayb52.sleeptight.tracking.NoiseEvents
import io.github.murrayb52.sleeptight.tracking.RecordingStore
import io.github.murrayb52.sleeptight.tracking.ServiceNightTracker
import io.github.murrayb52.sleeptight.tracking.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Manual dependency wiring: one instance per process, created by [SleepTightApplication]. */
class AppContainer(context: Context) {
    val clock: Clock = DeviceClock
    val database: SleepTightDatabase = SleepTightDatabase.create(context)
    val nightRepository = NightRepository(database, clock)

    /** Outlives screens and the service, for work that must finish (such as the last sample flush). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val recordingStore: RecordingStore = RoomRecordingStore(database)
    val noiseEvents: NoiseEvents = NoiseEvents.None

    /** Written by NightService, read by the UI. */
    val trackingStatus = MutableStateFlow(TrackingStatus())
    val nightTracker: NightTracker = ServiceNightTracker(context.applicationContext, trackingStatus)

    val alarmEvents: AlarmEvents = AlarmEvents.None
    val alarmScheduler = AlarmScheduler(context.applicationContext)

    /** Written by AlarmRingService, read by the alarm screen and Home. */
    val alarmState = MutableStateFlow<AlarmState>(AlarmState.Idle)
    val alarmController = AlarmController(context.applicationContext, nightRepository, alarmScheduler, alarmState, clock)
    val bedtimeReminders = BedtimeReminderScheduler(context.applicationContext, clock)

    val nightScorer = NightScorer(database)

    val garminSource: GarminSource = HealthConnectGarminSource(context.applicationContext)
    val garminImporter = GarminImporter(database, garminSource, nightScorer, clock)

    /**
     * Call when a night has just been closed (I'm up): tries Garmin first (it rescores on success),
     * otherwise scores from the sound estimate straight away. Garmin data that arrives later is
     * picked up on the next app start or from the night's page.
     */
    fun onNightClosed(nightId: Long) {
        appScope.launch {
            if (garminImporter.import(nightId) != GarminImporter.Result.IMPORTED) nightScorer.rescore(nightId)
        }
    }
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
