package io.github.murrayb52.sleeptight

import android.content.Context
import io.github.murrayb52.sleeptight.alarm.AlarmController
import io.github.murrayb52.sleeptight.alarm.AlarmEvents
import io.github.murrayb52.sleeptight.alarm.AlarmScheduler
import io.github.murrayb52.sleeptight.alarm.AlarmState
import io.github.murrayb52.sleeptight.alarm.BedtimeReminderScheduler
import io.github.murrayb52.sleeptight.data.Night
import io.github.murrayb52.sleeptight.data.isOpen
import io.github.murrayb52.sleeptight.diary.WakePromptRule
import io.github.murrayb52.sleeptight.tracking.Notifications
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
    private val appContext: Context = context.applicationContext
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

    /** Nights Garmin rewrote since they were imported, shown as a swipe-away note until dismissed. */
    val garminUpdates = MutableStateFlow<List<GarminImporter.Update>>(emptyList())

    /**
     * The screen-on time a wake note is waiting for, or null. Set when the screen turns on in the
     * night (see [WakePromptRule]) or from "Note a waking"; the app shows the note sheet while set.
     */
    val wakePromptAt = MutableStateFlow<Long?>(null)
    private var lastWakePromptAt = 0L

    /** Called by NightService whenever the screen turns on during a tracked night. */
    fun onScreenOnInNight(nightId: Long, at: Long) {
        appScope.launch {
            val night = nightRepository.getNight(nightId)?.takeIf { it.isOpen } ?: return@launch
            if (!WakePromptRule.shouldPrompt(at, night.bedTapAt, alarmState.value == AlarmState.Idle, lastWakePromptAt)) return@launch
            lastWakePromptAt = at
            wakePromptAt.value = at
            Notifications.showWakePrompt(appContext)
        }
    }

    /** Nights whose morning survey was put off with "Later" in this run of the app. */
    val surveyPutOff = MutableStateFlow<Set<Long>>(emptySet())

    /** A night whose survey was asked for from its page, shown even if it's old or was put off. */
    val surveyRequested = MutableStateFlow<Long?>(null)

    /**
     * Call when a night has just been closed (I'm up): tries Garmin first (it rescores on success),
     * otherwise scores from the sound estimate straight away. Garmin data that arrives later is
     * picked up on the next app start or from the night's page.
     */
    fun onNightClosed(nightId: Long) {
        appScope.launch {
            val result = garminImporter.import(nightId)
            if (result != GarminImporter.Result.IMPORTED && result != GarminImporter.Result.UPDATED) nightScorer.rescore(nightId)
        }
    }

    /**
     * I'm up from the alarm (its screen or notification): ends the open night now, stops tracking
     * and the alarm, then imports or scores the night. Returns the closed night, or null if none
     * was open.
     */
    suspend fun endNightFromAlarm(): Night? {
        val closed = try {
            nightRepository.wakeUp()
        } catch (e: IllegalArgumentException) {
            null // Only if the clock moved back past the bed tap; I'm up on Tonight explains it.
        } ?: return null
        nightTracker.stop()
        alarmController.disarm(closed.id)
        onNightClosed(closed.id)
        return closed
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
