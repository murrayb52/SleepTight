package io.github.murrayb52.sleeptight.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.murrayb52.sleeptight.SleepTightApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I'm up on a ringing alarm silences it and ends the night in one step, through the real ring
 * service and database (2026-11-05: it used to be a separate Stop, then I'm up).
 */
@RunWith(AndroidJUnit4::class)
class AlarmImUpTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container = (context as SleepTightApplication).container

    @Test
    fun imUpStopsTheRingingAndClosesTheNight() = runBlocking {
        container.nightRepository.getOpenNight()?.let { container.nightRepository.wakeUp() }
        val night = container.nightRepository.goToBed()
        Thread.sleep(50) // the wake tap must come after the bed tap

        context.startForegroundService(
            AlarmRingService.intent(context, AlarmRingService.ACTION_RING).putExtra(AlarmScheduler.EXTRA_NIGHT_ID, night.id),
        )
        waitUntil { container.alarmState.value is AlarmState.Ringing }

        context.startService(AlarmRingService.intent(context, AlarmRingService.ACTION_IM_UP))
        waitUntil { container.nightRepository.getNight(night.id)?.wakeTapAt != null }

        assertEquals(AlarmState.Idle, container.alarmState.value)
        assertNull(container.nightRepository.getOpenNight())
        assertNotNull(container.nightRepository.getNight(night.id)?.alarmFiredAt)
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000, condition: suspend () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            Thread.sleep(50)
        }
    }
}
