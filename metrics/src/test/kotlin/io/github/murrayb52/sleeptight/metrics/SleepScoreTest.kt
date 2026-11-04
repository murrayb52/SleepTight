package io.github.murrayb52.sleeptight.metrics

import io.github.murrayb52.sleeptight.metrics.ScoreComponent.DEEP_REM
import io.github.murrayb52.sleeptight.metrics.ScoreComponent.DURATION
import io.github.murrayb52.sleeptight.metrics.ScoreComponent.EFFICIENCY
import io.github.murrayb52.sleeptight.metrics.ScoreComponent.INTERRUPTIONS
import io.github.murrayb52.sleeptight.metrics.ScoreComponent.REGULARITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepScoreTest {
    private val eps = 1e-9
    private val perfectRegularity = Regularity(bedtime = 1.0, duration = 1.0, snoozes = 1.0, consistency = 1.0)

    private fun score(input: NightInput, regularity: Regularity = perfectRegularity) =
        SleepScore.of(NightMetrics.of(input), regularity)!!

    private fun SleepScore.points(c: ScoreComponent) = component(c)!!.points
    private fun SleepScore.max(c: ScoreComponent) = component(c)!!.maxPoints

    /** A Garmin night with the given minutes of sleep split into light, deep and REM, from 22:00. */
    private fun garmin(light: Long, deep: Long, rem: Long, awakeFirst: Long = 0, wake: String? = null) =
        night(wake = wake) {
            if (awakeFirst > 0) awake(awakeFirst)
            light(light); deep(deep); rem(rem)
        }

    @Test
    fun `a textbook night scores 100`() {
        // 8 h asleep, 100 % efficiency, 40 % deep + REM, no interruptions.
        val s = score(garmin(light = 288, deep = 96, rem = 96))
        assertEquals(100, s.total)
        assertEquals(35.0, s.points(DURATION), eps)
        assertEquals(25.0, s.points(EFFICIENCY), eps)
        assertEquals(20.0, s.points(DEEP_REM), eps)
        assertEquals(10.0, s.points(INTERRUPTIONS), eps)
        assertEquals(10.0, s.points(REGULARITY), eps)
        assertFalse(s.deepRemRedistributed)
    }

    @Test
    fun `duration scales up to 8 hours and caps there`() {
        assertEquals(35.0 * 7 / 8, score(garmin(light = 252, deep = 84, rem = 84)).points(DURATION), eps)
        assertEquals(35.0, score(garmin(light = 324, deep = 108, rem = 108)).points(DURATION), eps)
    }

    @Test
    fun `efficiency is zero at 70 percent and full at 90`() {
        fun efficiencyPoints(asleep: Long) =
            score(garmin(light = asleep - 200, deep = 100, rem = 100, awakeFirst = 480 - asleep)).points(EFFICIENCY)
        assertEquals(0.0, efficiencyPoints(300), eps) // 62.5 %
        assertEquals(0.0, efficiencyPoints(336), eps) // 70 %
        assertEquals(12.5, efficiencyPoints(384), eps) // 80 %
        assertEquals(18.75, efficiencyPoints(408), eps) // 85 %
        assertEquals(25.0, efficiencyPoints(432), eps) // 90 %
        assertEquals(25.0, efficiencyPoints(470), eps)
    }

    @Test
    fun `deep plus REM is zero at 20 percent and full at 40`() {
        assertEquals(0.0, score(garmin(light = 400, deep = 40, rem = 40)).points(DEEP_REM), 1e-9) // 16.7 %
        assertEquals(0.0, score(garmin(light = 384, deep = 48, rem = 48)).points(DEEP_REM), eps) // 20 %
        assertEquals(10.0, score(garmin(light = 336, deep = 72, rem = 72)).points(DEEP_REM), eps) // 30 %
        assertEquals(20.0, score(garmin(light = 240, deep = 120, rem = 120)).points(DEEP_REM), eps) // 50 %
    }

    @Test
    fun `interruptions lose 2 per wake-up and 1 per noise cluster, never below zero`() {
        val twoWakeUpsThreeClusters = night {
            light(150); awake(5); deep(150); awake(5); rem(170)
            noiseAt(30); noiseAt(200); noiseAt(400)
        }
        assertEquals(3.0, score(twoWakeUpsThreeClusters).points(INTERRUPTIONS), eps)

        val restless = night {
            repeat(6) { light(60); awake(5) }
            light(60)
        }
        assertEquals(0.0, score(restless).points(INTERRUPTIONS), eps)
    }

    @Test
    fun `regularity is the mean factor times 10`() {
        val r = Regularity(bedtime = 0.5, duration = 1.0, snoozes = 0.0, consistency = null)
        assertEquals(5.0, score(goodNight(), r).points(REGULARITY), eps)
    }

    @Test
    fun `without Garmin stages, deep plus REM's weight is spread across the rest`() {
        val s = score(night(source = SleepDataSource.ESTIMATED) { unknown(480) })
        assertTrue(s.deepRemRedistributed)
        assertNull(s.component(DEEP_REM))
        assertEquals(43.75, s.max(DURATION), eps)
        assertEquals(31.25, s.max(EFFICIENCY), eps)
        assertEquals(12.5, s.max(INTERRUPTIONS), eps)
        assertEquals(12.5, s.max(REGULARITY), eps)
        assertEquals(100, s.total)
    }

    @Test
    fun `redistributed interruptions keep the same proportional penalty`() {
        val s = score(night(source = SleepDataSource.ESTIMATED) { unknown(200); awake(5); unknown(275) })
        // 10 - 2 = 8 of 10 → 80 % of 12.5.
        assertEquals(10.0, s.points(INTERRUPTIONS), eps)
    }

    @Test
    fun `components carry their raw fraction for the breakdown`() {
        val s = score(garmin(light = 208, deep = 100, rem = 100, awakeFirst = 72)) // 85 % efficient
        val efficiency = s.component(EFFICIENCY)!!
        assertEquals(0.75, efficiency.fraction, eps)
        assertEquals(18.75, efficiency.points, eps)
        assertEquals(25.0, efficiency.maxPoints, eps)
    }

    @Test
    fun `total is the rounded sum of the components`() {
        val s = score(goodNight(), Regularity(bedtime = 0.5, duration = 1.0, snoozes = 1.0, consistency = null))
        assertEquals(Math.round(s.components.sumOf { it.points }).toInt(), s.total)
    }

    @Test
    fun `no sleep data, no score`() {
        val m = NightMetrics.of(night(wake = "2026-11-04T06:00") {}.copy(source = null))
        assertNull(SleepScore.of(m, perfectRegularity))
    }

    @Test
    fun `weights add up to 100`() {
        val c = ScoreConfig()
        assertEquals(100.0, c.durationWeight + c.efficiencyWeight + c.deepRemWeight + c.interruptionsWeight + c.regularityWeight, eps)
    }
}
