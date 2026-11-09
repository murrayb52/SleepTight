package io.github.murrayb52.sleeptight.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import io.github.murrayb52.sleeptight.SleepTightApplication
import io.github.murrayb52.sleeptight.metrics.SnoozeLadder
import io.github.murrayb52.sleeptight.metrics.VolumeRamp
import io.github.murrayb52.sleeptight.tracking.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * Rings the alarm: plays the chime on the alarm stream with a 2 → 100 % ease-in over 3 minutes (no
 * vibration) and shows a full-screen alarm. Handles snooze (counted and recorded, with the number
 * and lengths set in Profile) and dismiss.
 */
class AlarmRingService : Service() {
    private val container get() = (application as SleepTightApplication).container
    private val state get() = container.alarmState

    private var player: AlarmPlayer? = null
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
            ACTION_IM_UP -> {
                dismiss()
                container.appScope.launch { container.endNightFromAlarm() }
            }
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
            // The chosen sound (a broken file falls back to the chime). Started here, then the ramp takes over.
            val sound = AlarmSound.of(repo.getSettings().alarmSound)
            withContext(Dispatchers.Main) {
                if (state.value is AlarmState.Ringing && player == null) {
                    player = try {
                        AlarmPlayer.create(this@AlarmRingService, sound).apply { start(VolumeRamp.at(Duration.ZERO)) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Couldn't play the alarm sound", e) // the screen still wakes you
                        null
                    }
                }
            }
            repo.markAlarmFired(nightId, now)
            // Work out the next snooze, then offer it (or not) on the screen and the notification.
            // The snooze settings as they are now, so a change in Profile applies from the next ring.
            val settings = repo.getSettings()
            val curve = settings.snoozeCurveOrDefault()
            val used = repo.getNight(nightId)?.snoozeCount ?: 0
            val next = SnoozeLadder.minutesFor(used, settings.snoozesAllowed, curve, settings.alarmConfig())
            val current = state.value
            if (current is AlarmState.Ringing && current.nightId == nightId) {
                state.value = current.copy(
                    nextSnoozeMinutes = next,
                    snoozesUsed = used,
                    snoozesAllowed = settings.snoozesAllowed,
                    snoozeCurve = curve.name,
                )
                Notifications.updateAlarmRinging(this@AlarmRingService, next)
            }
        }
        container.alarmEvents.onAlarmFired(nightId)

        rampJob = container.appScope.launch {
            while (isActive) {
                val elapsed = Duration.between(now, container.clock.instant())
                player?.setVolume(VolumeRamp.at(elapsed))
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
            repo.addSnooze(ringing.nightId, minutes, ringing.snoozeCurve)
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
     * Stops the alarm without ending the night: when it has rung unanswered for [MAX_RING], or as
     * part of I'm up ([ACTION_IM_UP], which then ends the night).
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

    private fun stopSound() {
        rampJob?.cancel()
        rampJob = null
        player?.release()
        player = null
    }

    companion object {
        private const val TAG = "AlarmRingService"
        const val ACTION_RING = "io.github.murrayb52.sleeptight.action.RING_START"
        const val ACTION_SNOOZE = "io.github.murrayb52.sleeptight.action.SNOOZE"
        /** Stops the alarm (Stop, I'm up, or no answer for 10 minutes). The night stays open. */
        const val ACTION_DISMISS = "io.github.murrayb52.sleeptight.action.DISMISS"

        /** I'm up: stops the alarm and ends the night in one tap (2026-11-05). */
        const val ACTION_IM_UP = "io.github.murrayb52.sleeptight.action.IM_UP"

        /** An unanswered alarm stops after this long rather than ringing all day. */
        val MAX_RING: Duration = Duration.ofMinutes(10)

        fun intent(context: Context, action: String) = Intent(context, AlarmRingService::class.java).setAction(action)
    }
}
