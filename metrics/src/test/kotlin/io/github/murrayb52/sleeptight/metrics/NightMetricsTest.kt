package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NightMetricsTest {
    private val eps = 1e-9

    @Test
    fun `a good night`() {
        val m = NightMetrics.of(goodNight())
        assertEquals(minutes(480), m.timeInBed)
        assertEquals(at("2026-11-03T22:10"), m.sleepOnset)
        assertEquals(minutes(10), m.timeToFallAsleep)
        assertEquals(minutes(470), m.totalSleep)
        assertEquals(0, m.wakeUps)
        assertEquals(470.0 / 480, m.efficiency!!, eps)
        assertEquals(110.0 / 470, m.deepShare!!, eps)
        assertEquals(110.0 / 470, m.remShare!!, eps)
        assertEquals(220.0 / 470, m.deepRemShare!!, eps)
    }

    @Test
    fun `awake spells of 2 minutes or more after onset count as wake-ups`() {
        val m = NightMetrics.of(
            night {
                awake(20) // before onset: not a wake-up
                light(60); awake(1) // too short
                light(60); awake(2) // counts
                deep(60); awake(15) // counts
                rem(60)
            },
        )
        assertEquals(2, m.wakeUps)
    }

    @Test
    fun `back-to-back awake segments are one wake-up`() {
        val m = NightMetrics.of(night { light(60); awake(1); awake(1); awake(1); light(60) })
        assertEquals(1, m.wakeUps)
    }

    @Test
    fun `the final awakening before the wake tap is not a wake-up`() {
        val m = NightMetrics.of(night { light(400); awake(30) })
        assertEquals(0, m.wakeUps)
        assertEquals(minutes(400), m.totalSleep)
    }

    @Test
    fun `segments are trimmed to the taps`() {
        // Garmin thinks sleep started 30 min before the bed tap and ended 30 min after the wake tap.
        val input = night(bed = "2026-11-03T21:30", wake = "2026-11-04T05:30") { light(540) }
            .copy(bedTapAt = at("2026-11-03T22:00"))
        val m = NightMetrics.of(input)
        assertEquals(minutes(450), m.timeInBed)
        assertEquals(minutes(450), m.totalSleep)
        assertEquals(at("2026-11-03T22:00"), m.sleepOnset)
        assertEquals(minutes(0), m.timeToFallAsleep)
        assertEquals(1.0, m.efficiency!!, eps)
    }

    @Test
    fun `segments entirely outside the taps are ignored`() {
        val input = night(wake = "2026-11-04T06:00") { light(480); deep(60) }
        val m = NightMetrics.of(input)
        assertEquals(minutes(480), m.totalSleep)
        assertEquals(0.0, m.deepShare!!, eps)
    }

    @Test
    fun `overlapping segments are not double counted`() {
        val bed = at("2026-11-03T22:00")
        val input = NightInput(
            bedTapAt = bed,
            wakeTapAt = bed.plus(minutes(120)),
            segments = listOf(
                SleepSegment(bed, bed.plus(minutes(90)), Stage.LIGHT),
                SleepSegment(bed.plus(minutes(60)), bed.plus(minutes(120)), Stage.DEEP),
            ),
            source = SleepDataSource.GARMIN,
        )
        val m = NightMetrics.of(input)
        assertEquals(minutes(120), m.totalSleep)
        assertEquals(30.0 / 120, m.deepShare!!, eps)
    }

    @Test
    fun `gaps in the data count as neither sleep nor wake-ups`() {
        val m = NightMetrics.of(night { light(200); gap(30); light(250) })
        assertEquals(minutes(450), m.totalSleep)
        assertEquals(0, m.wakeUps)
        assertEquals(450.0 / 480, m.efficiency!!, eps)
    }

    @Test
    fun `unknown stage counts as asleep`() {
        val m = NightMetrics.of(night(source = SleepDataSource.ESTIMATED) { awake(20); unknown(460) })
        assertEquals(minutes(460), m.totalSleep)
        assertEquals(minutes(20), m.timeToFallAsleep)
    }

    @Test
    fun `estimated nights have no deep or REM shares`() {
        val m = NightMetrics.of(goodNight(SleepDataSource.ESTIMATED))
        assertEquals(minutes(470), m.totalSleep)
        assertNull(m.deepShare)
        assertNull(m.remShare)
        assertNull(m.deepRemShare)
    }

    @Test
    fun `no sleep data means no sleep metrics, but time in bed is still known`() {
        val input = night(wake = "2026-11-04T06:00") {}.copy(source = null)
        val m = NightMetrics.of(input)
        assertEquals(minutes(480), m.timeInBed)
        assertNull(m.totalSleep)
        assertNull(m.sleepOnset)
        assertNull(m.timeToFallAsleep)
        assertNull(m.wakeUps)
        assertNull(m.efficiency)
        assertEquals(false, m.hasSleepData)
    }

    @Test
    fun `a night spent awake has no onset and zero sleep`() {
        val m = NightMetrics.of(night { awake(480) })
        assertNull(m.sleepOnset)
        assertNull(m.timeToFallAsleep)
        assertEquals(minutes(0), m.totalSleep)
        assertEquals(0, m.wakeUps)
        assertEquals(0.0, m.efficiency!!, eps)
        assertNull(m.deepShare)
    }

    @Test
    fun `noise events within 5 minutes of each other form one cluster`() {
        val m = NightMetrics.of(
            night {
                light(480)
                noiseAt(60); noiseAt(63); noiseAt(67) // one cluster: each within 5 min of the last
                noiseAt(120) // a second
                noiseAt(300); noiseAt(306) // 6 min apart: two more
            },
        )
        assertEquals(6, m.noiseEvents.size)
        assertEquals(4, m.noiseClusters)
    }

    @Test
    fun `noise outside the taps is ignored`() {
        val m = NightMetrics.of(night { light(480); noiseAt(-10); noiseAt(100); noiseAt(500) })
        assertEquals(1, m.noiseEvents.size)
        assertEquals(1, m.noiseClusters)
    }
}
