package io.github.murrayb52.sleeptight.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Starts and stops [NightService]. Call [start] only while the app is in the foreground. */
class ServiceNightTracker(
    private val context: Context,
    private val mutableStatus: MutableStateFlow<TrackingStatus>,
) : NightTracker {
    override val status: StateFlow<TrackingStatus> = mutableStatus

    override fun start(nightId: Long) {
        // Checked here, not in the service: a foreground service that stops before calling
        // startForeground crashes the app, and Android won't start a microphone service without it.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableStatus.value = TrackingStatus(
                TrackingStatus.State.STOPPED_WITH_PROBLEM,
                nightId,
                problem = TrackingStatus.Problem.MIC_PERMISSION,
            )
            return
        }
        mutableStatus.value = TrackingStatus(TrackingStatus.State.STARTING, nightId)
        Notifications.cancelTrackingStopped(context)
        ContextCompat.startForegroundService(context, NightService.startIntent(context, nightId))
    }

    override fun stop() {
        context.stopService(Intent(context, NightService::class.java))
    }
}
