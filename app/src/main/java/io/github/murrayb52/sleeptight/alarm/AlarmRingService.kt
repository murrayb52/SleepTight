package io.github.murrayb52.sleeptight.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.metrics.SnoozeLadder
import io.github.murrayb52.sleeptight.metrics.VolumeRamp
import io.github.murrayb52.sleeptight.tracking.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Rings the alarm: plays the chime on the alarm stream with a 10 → 100 % ramp over 60 s (no
 * vibration) and shows a full-screen alarm. Handles snooze (counted, shorter each time, at most 5)
 * and dismiss.
 */
class AlarmRingService : Service() {
    private val container get() = (application as SleepTightApplication).container
    private val state get() = container.alarmState

    private var track: AudioTrack? = null
    private var rampJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                // Started with startForegroundService, so this must come first.
                ServiceCompat.startForeground(
                    this,
                    Notifications.ALARM_ID,
                    Notifications.alarmRinging(this),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
                )
                val nightId = intent.getLongExtra(AlarmScheduler.EXTRA_NIGHT_ID, 0)
                if (state.value !is AlarmState.Ringing) startRinging(nightId)
            }
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSound()
        super.onDestroy()
    }

    private fun startRinging(nightId: Long) {
        val now = container.clock.instant()
        state.value = AlarmState.Ringing(nightId, now)
        Notifications.cancelSnoozed(this)
        container.appScope.launch {
            val repo = container.nightRepository
            repo.markAlarmFired(nightId, now)
            // Work out the next snooze, then offer it (or not) on the screen and the notification.
            val used = repo.getNight(nightId)?.snoozeCount ?: 0
            val next = SnoozeLadder.minutesFor(used)
            val current = state.value
            if (current is AlarmState.Ringing && current.nightId == nightId) {
                state.value = current.copy(nextSnoozeMinutes = next, snoozesUsed = used)
                Notifications.updateAlarmRinging(this@AlarmRingService, next)
            }
        }
        container.alarmEvents.onAlarmFired(nightId)

        track = try {
            buildTrack().apply {
                setVolume(VolumeRamp.at(Duration.ZERO))
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't play the chime", e) // vibration and the screen still wake you
            null
        }
        rampJob = container.appScope.launch {
            while (isActive) {
                val elapsed = Duration.between(now, container.clock.instant())
                track?.setVolume(VolumeRamp.at(elapsed))
                if (elapsed >= MAX_RING) {
                    Log.i(TAG, "Rang for $MAX_RING with no answer; stopping")
                    dismiss()
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun snooze() {
        val ringing = state.value as? AlarmState.Ringing ?: return stopIfIdle()
        // No snoozes left: the only way out is Stop.
        val minutes = ringing.nextSnoozeMinutes ?: return
        stopSound()
        container.appScope.launch {
            val repo = container.nightRepository
            repo.addSnooze(ringing.nightId)
            val count = repo.getNight(ringing.nightId)?.snoozeCount ?: 0
            val until = container.clock.instant().plus(Duration.ofMinutes(minutes.toLong()))
            container.alarmScheduler.scheduleRing(ringing.nightId, until)
            state.value = AlarmState.Snoozed(ringing.nightId, until)
            container.alarmEvents.onSnooze(ringing.nightId, count)
            Notifications.showSnoozed(this@AlarmRingService, until)
            finish()
        }
    }

    /**
     * Stops the alarm. It never ends the night: that's always the separate "I'm up" tap, which the
     * alarm screen offers straight after Stop.
     */
    private fun dismiss() {
        val nightId = when (val s = state.value) {
            is AlarmState.Ringing -> s.nightId
            is AlarmState.Snoozed -> s.nightId
            AlarmState.Idle -> null
        }
        stopSound()
        container.alarmScheduler.cancel()
        state.value = AlarmState.Idle
        Notifications.cancelSnoozed(this)
        if (nightId != null) container.alarmEvents.onDismiss(nightId)
        finish()
    }

    private fun stopIfIdle() {
        if (state.value == AlarmState.Idle) finish()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildTrack(): AudioTrack {
        val pcm = Chime.generate()
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Chime.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .apply {
                write(pcm, 0, pcm.size)
                setLoopPoints(0, pcm.size, -1)
            }
    }

    private fun stopSound() {
        rampJob?.cancel()
        rampJob = null
        track?.run {
            try {
                stop()
            } catch (e: IllegalStateException) {
                // Already stopped.
            }
            release()
        }
        track = null
    }

    companion object {
        private const val TAG = "AlarmRingService"
        const val ACTION_RING = "io.github.murrayb52.sleeptight.action.RING_START"
        const val ACTION_SNOOZE = "io.github.murrayb52.sleeptight.action.SNOOZE"
        /** Stops the alarm (Stop, I'm up, or no answer for 10 minutes). The night stays open. */
        const val ACTION_DISMISS = "io.github.murrayb52.sleeptight.action.DISMISS"

        /** An unanswered alarm stops after this long rather than ringing all day. */
        val MAX_RING: Duration = Duration.ofMinutes(10)

        fun intent(context: Context, action: String) = Intent(context, AlarmRingService::class.java).setAction(action)
    }
}
