package io.github.murrayb52.sleeptight.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.data.ScreenEvent
import io.github.murrayb52.sleeptight.data.ScreenEventType
import io.github.murrayb52.sleeptight.data.isOpen
import io.github.murrayb52.sleeptight.metrics.LevelAccumulator
import io.github.murrayb52.sleeptight.metrics.LevelSample
import io.github.murrayb52.sleeptight.metrics.SmartTrigger
import io.github.murrayb52.sleeptight.metrics.SoundLevel
import io.github.murrayb52.sleeptight.metrics.activityPerMinute
import io.github.murrayb52.sleeptight.tracking.TrackingStatus.Problem
import io.github.murrayb52.sleeptight.tracking.TrackingStatus.State
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service (type `microphone`) that measures sound level once a second while a night is
 * open. Audio buffers are only ever held in memory to compute a level. Nothing is written to disk
 * except the levels and noise events, via [NightRecorder].
 */
class NightService : Service() {
    private val container get() = (application as SleepTightApplication).container
    private val status get() = container.trackingStatus

    private var job: Job? = null
    private var recordingNightId: Long? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must come first: a service started with startForegroundService that
        // stops before calling it crashes the app.
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.TRACKING_ID,
                Notifications.tracking(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or SecurityException: typically a sticky
            // restart from the background, which Android doesn't allow for the microphone.
            Log.w(TAG, "Couldn't start in the foreground", e)
            status.value = TrackingStatus(State.STOPPED_WITH_PROBLEM, recordingNightId, problem = Problem.SERVICE_NOT_ALLOWED)
            Notifications.showTrackingStopped(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val requested = intent?.getLongExtra(EXTRA_NIGHT_ID, NO_NIGHT)?.takeIf { it != NO_NIGHT }
        if (job?.isActive == true && (requested == null || requested == recordingNightId)) return START_STICKY

        job?.cancel()
        job = container.appScope.launch {
            val repo = container.nightRepository
            val night = requested?.let { repo.getNight(it) } ?: repo.getOpenNight()
            if (night == null || !night.isOpen) {
                Log.i(TAG, "No open night to track; stopping")
                stopSelf()
                return@launch
            }
            recordingNightId = night.id
            record(night.id)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        releaseWakeLock()
        if (status.value.state != State.STOPPED_WITH_PROBLEM) status.value = TrackingStatus()
        super.onDestroy()
    }

    private suspend fun record(nightId: Long) = withContext(Dispatchers.IO) {
        status.value = TrackingStatus(State.STARTING, nightId)
        acquireWakeLock()
        val recorder = NightRecorder(nightId, container.recordingStore, container.noiseEvents)
        val repo = container.nightRepository
        recorder.seedActivity(
            activityPerMinute(repo.soundSamples(nightId).map { LevelSample(Instant.ofEpochMilli(it.timestamp), it.dbRelative.toDouble()) }),
        )
        val smartAlarm = SmartAlarmCheck(nightId)
        val audio = openAudioRecord()
        if (audio == null) {
            fail(nightId, Problem.MIC_UNAVAILABLE)
            return@withContext
        }
        val screen = registerScreenEvents(nightId)
        // Read in 100 ms chunks: each chunk updates the live display; ten make the stored second.
        val chunk = ShortArray(SAMPLE_RATE / CHUNKS_PER_SECOND)
        val second = LevelAccumulator(SAMPLE_RATE)
        var live: LiveLevel? = null
        try {
            audio.startRecording()
            while (currentCoroutineContext().isActive) {
                var filled = 0
                while (filled < chunk.size) {
                    val n = audio.read(chunk, filled, chunk.size - filled)
                    check(n >= 0) { "AudioRecord.read returned $n" }
                    filled += n
                }
                val chunkDbfs = second.add(chunk, filled)
                if (second.samples >= SAMPLE_RATE) {
                    val secondStart = container.clock.instant().minusSeconds(1)
                    val features = second.features()
                    live = recorder.onLevel(secondStart, features.dbfs, features)
                    second.reset()
                    smartAlarm.check(recorder)
                }
                // The display level is relative to the same baseline as the stored one.
                val baseline = live?.baselineDbfs ?: chunkDbfs
                status.value = TrackingStatus(State.RECORDING, nightId, live, displayDbRelative = chunkDbfs - baseline)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            fail(nightId, Problem.MIC_UNAVAILABLE)
        } finally {
            audio.stop()
            audio.release()
            unregisterReceiver(screen)
            withContext(NonCancellable) { recorder.flush() }
        }
    }

    /**
     * Logs the screen turning on, off and being unlocked while the night is tracked: picking the
     * phone up is a strong sign of being awake, for comparing against sound and Garmin later.
     * Only the time and the kind of event are kept.
     */
    private fun registerScreenEvents(nightId: Long): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val type = when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> ScreenEventType.ON
                    Intent.ACTION_SCREEN_OFF -> ScreenEventType.OFF
                    Intent.ACTION_USER_PRESENT -> ScreenEventType.UNLOCKED
                    else -> return
                }
                val at = container.clock.millis()
                container.appScope.launch {
                    container.database.screenEventDao().insert(ScreenEvent(nightId = nightId, timestamp = at, type = type))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return receiver
    }

    /**
     * Once a minute inside the wake window, asks [SmartTrigger] whether you're stirring. If so, the
     * alarm is rescheduled to ring now. Only one early ring per night, and none once it has rung.
     */
    private inner class SmartAlarmCheck(private val nightId: Long) {
        private var lastMinute = -1L
        private var done = false

        suspend fun check(recorder: NightRecorder) {
            if (done) return
            val now = container.clock.instant()
            val minute = now.toEpochMilli() / 60_000
            if (minute == lastMinute) return
            lastMinute = minute
            val night = container.nightRepository.getNight(nightId) ?: return
            val start = night.alarmWindowStart ?: return
            val end = night.alarmWindowEnd ?: return
            if (night.alarmFiredAt != null) {
                done = true
                return
            }
            // Skip the last minute of the window: the scheduled ring is about to go off anyway.
            if (now.toEpochMilli() !in start until end - 60_000) return
            if (SmartTrigger.shouldWakeNow(recorder.activity, now)) {
                Log.i(TAG, "Smart alarm: stirring detected, ringing early")
                done = true
                container.alarmController.ringNow(nightId)
            }
        }
    }

    private fun fail(nightId: Long, problem: Problem) {
        status.value = TrackingStatus(State.STOPPED_WITH_PROBLEM, nightId, problem = problem)
        Notifications.showTrackingStopped(this)
        stopSelf()
    }

    @SuppressLint("MissingPermission") // checked just above
    private fun openAudioRecord(): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(minBuffer, SAMPLE_RATE * 2)
        // VOICE_RECOGNITION usually skips automatic gain control, which would flatten the levels.
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            try {
                val record = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
                if (record.state == AudioRecord.STATE_INITIALIZED) return record
                record.release()
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord source $source failed", e)
            }
        }
        return null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Keeps the CPU running (not the screen) so the mic keeps being read with the screen off.
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepTight:night").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val TAG = "NightService"
        const val SAMPLE_RATE = 16_000
        private const val CHUNKS_PER_SECOND = 10
        private const val EXTRA_NIGHT_ID = "nightId"
        private const val NO_NIGHT = -1L

        /** Safety net in case the night is never closed. */
        private const val WAKE_LOCK_TIMEOUT_MS = 20L * 60 * 60 * 1000

        fun startIntent(context: Context, nightId: Long) =
            Intent(context, NightService::class.java).putExtra(EXTRA_NIGHT_ID, nightId)
    }
}
