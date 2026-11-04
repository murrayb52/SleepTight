package io.github.murrayb52.sleeptight.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SleepEstimatorTest {
    private val bed = at("2026-11-03T22:00")

    /** Minutes from [start], each with the given active seconds; null leaves a minute missing. */
    private fun minutes(vararg active: Int?, start: Instant = bed) =
        active.mapIndexedNotNull { i, a -> a?.let { MinuteActivity(start.plus(Duration.ofMinutes(i.toLong())), it) } }

    private fun run(n: Int, active: Int) = List(n) { active }

    private fun estimate(activity: List<MinuteActivity>, wake: Instant = bed.plus(Duration.ofMinutes(activity.size.toLong()))) =
        SleepEstimator.estimate(activity, bed, wake)

    private fun SleepSegment.minutes() = Duration.between(start, end).toMinutes()

    @Test
    fun `no activity data, no estimate`() {
        assertTrue(estimate(emptyList(), wake = bed.plus(Duration.ofHours(8))).isEmpty())
    }

    @Test
    fun `a quiet night is one stretch of sleep`() {
        val s = estimate(minutes(*run(480, 0).toTypedArray()))
        assertEquals(1, s.size)
        assertEquals(Stage.UNKNOWN, s[0].stage)
        assertEquals(bed, s[0].start)
        assertEquals(480, s[0].minutes())
    }

    @Test
    fun `restless minutes are awake and quiet runs of 20 or more are asleep`() {
        val activity = minutes(*(run(10, 6) + run(300, 0) + run(5, 9) + run(15, 0) + run(3, 4)).toTypedArray())
        val s = estimate(activity)
        assertEquals(listOf(Stage.AWAKE, Stage.UNKNOWN, Stage.AWAKE), s.map { it.stage })
        assertEquals(listOf(10L, 300L, 23L), s.map { it.minutes() }) // the 15 quiet minutes are too short to count
    }

    @Test
    fun `exactly 20 quiet minutes count as sleep, 19 don't`() {
        assertEquals(Stage.UNKNOWN, estimate(minutes(*(run(5, 5) + run(20, 0) + run(5, 5)).toTypedArray()))[1].stage)
        assertEquals(listOf(Stage.AWAKE), estimate(minutes(*(run(5, 5) + run(19, 0) + run(5, 5)).toTypedArray())).map { it.stage })
    }

    @Test
    fun `fewer than 3 active seconds is still a quiet minute`() {
        val s = estimate(minutes(*(run(30, 2) + run(30, 3)).toTypedArray()))
        assertEquals(listOf(Stage.UNKNOWN, Stage.AWAKE), s.map { it.stage })
    }

    @Test
    fun `missing minutes leave a gap rather than a guess`() {
        val activity = minutes(*(run(60, 0) + List<Int?>(30) { null } + run(60, 0)).toTypedArray())
        val s = SleepEstimator.estimate(activity, bed, bed.plus(Duration.ofMinutes(150)))
        assertEquals(2, s.size)
        assertEquals(bed.plus(Duration.ofMinutes(60)), s[0].end)
        assertEquals(bed.plus(Duration.ofMinutes(90)), s[1].start)
    }

    @Test
    fun `segments are trimmed to the taps`() {
        // Minutes start on the minute; the bed tap was 30 s into the first one, the wake tap 30 s into the last.
        val activity = minutes(*run(60, 0).toTypedArray())
        val tappedBed = bed.plusSeconds(30)
        val s = SleepEstimator.estimate(activity, tappedBed, bed.plus(Duration.ofMinutes(59)).plusSeconds(30))
        assertEquals(tappedBed, s.first().start)
        assertEquals(bed.plus(Duration.ofMinutes(59)).plusSeconds(30), s.last().end)
    }

    @Test
    fun `the estimate scores as an estimated night`() {
        val activity = minutes(*(run(15, 6) + run(450, 0) + run(15, 6)).toTypedArray())
        val input = NightInput(bed, bed.plus(Duration.ofMinutes(480)), SleepEstimator.estimate(activity, bed, bed.plus(Duration.ofMinutes(480))), SleepDataSource.ESTIMATED)
        val m = NightMetrics.of(input)
        assertEquals(Duration.ofMinutes(450), m.totalSleep)
        assertEquals(Duration.ofMinutes(15), m.timeToFallAsleep)
        assertTrue(SleepScore.of(m, Regularity(1.0, 1.0, 1.0, null))!!.deepRemRedistributed)
    }
}
