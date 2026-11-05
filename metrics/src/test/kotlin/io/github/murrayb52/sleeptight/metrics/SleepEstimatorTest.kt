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
    fun `falling asleep takes 10 quiet minutes`() {
        // Restless, then 9 quiet minutes broken by a restless one, then settled.
        val s = estimate(minutes(*(run(10, 9) + run(9, 0) + run(1, 9) + run(30, 0)).toTypedArray()))
        assertEquals(listOf(Stage.AWAKE, Stage.UNKNOWN), s.map { it.stage })
        assertEquals(listOf(20L, 30L), s.map { it.minutes() })
    }

    @Test
    fun `exactly 10 quiet minutes count as falling asleep, 9 don't`() {
        assertEquals(Stage.UNKNOWN, estimate(minutes(*(run(5, 9) + run(10, 0) + run(5, 9)).toTypedArray()))[1].stage)
        assertEquals(listOf(Stage.AWAKE), estimate(minutes(*(run(5, 9) + run(9, 0) + run(5, 9)).toTypedArray())).map { it.stage })
    }

    @Test
    fun `once asleep, a restless minute is a brief stir and sleep resumes straight after`() {
        // The first overnight test (2026-11-05) scored 1 h 43 min against Garmin's 7 h 1 min, because
        // every stir needed 20 quiet minutes after it before sleep counted again. A stir now costs
        // only its own minutes.
        val s = estimate(minutes(*(run(30, 0) + run(1, 9) + run(10, 0) + run(2, 9) + run(30, 0)).toTypedArray()))
        assertEquals(listOf(Stage.UNKNOWN, Stage.AWAKE, Stage.UNKNOWN, Stage.AWAKE, Stage.UNKNOWN), s.map { it.stage })
        assertEquals(listOf(30L, 1L, 10L, 2L, 30L), s.map { it.minutes() })
    }

    @Test
    fun `after 15 minutes awake, falling asleep again takes 10 quiet minutes`() {
        val s = estimate(minutes(*(run(30, 0) + run(15, 9) + run(9, 0) + run(1, 9) + run(25, 0)).toTypedArray()))
        assertEquals(listOf(Stage.UNKNOWN, Stage.AWAKE, Stage.UNKNOWN), s.map { it.stage })
        assertEquals(listOf(30L, 25L, 25L), s.map { it.minutes() })
    }

    @Test
    fun `14 minutes awake is still a wake-up within sleep`() {
        val s = estimate(minutes(*(run(30, 0) + run(14, 9) + run(5, 0)).toTypedArray()))
        assertEquals(listOf(Stage.UNKNOWN, Stage.AWAKE, Stage.UNKNOWN), s.map { it.stage })
        assertEquals(listOf(30L, 14L, 5L), s.map { it.minutes() })
    }

    @Test
    fun `fewer than 5 active seconds is still a quiet minute`() {
        val s = estimate(minutes(*(run(30, 4) + run(30, 5)).toTypedArray()))
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
    fun `after a long gap, falling asleep again takes 10 quiet minutes`() {
        val activity = minutes(*(run(30, 0) + List<Int?>(15) { null } + run(5, 0)).toTypedArray())
        val s = SleepEstimator.estimate(activity, bed, bed.plus(Duration.ofMinutes(50)))
        assertEquals(listOf(Stage.UNKNOWN, Stage.AWAKE), s.map { it.stage })
    }

    @Test
    fun `a short gap doesn't interrupt sleep`() {
        val activity = minutes(*(run(30, 0) + List<Int?>(2) { null } + run(5, 0)).toTypedArray())
        val s = SleepEstimator.estimate(activity, bed, bed.plus(Duration.ofMinutes(37)))
        assertEquals(listOf(Stage.UNKNOWN, Stage.UNKNOWN), s.map { it.stage })
        assertEquals(listOf(30L, 5L), s.map { it.minutes() })
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
        val activity = minutes(*(run(15, 9) + run(450, 0) + run(15, 9)).toTypedArray())
        val input = NightInput(bed, bed.plus(Duration.ofMinutes(480)), SleepEstimator.estimate(activity, bed, bed.plus(Duration.ofMinutes(480))), SleepDataSource.ESTIMATED)
        val m = NightMetrics.of(input)
        assertEquals(Duration.ofMinutes(450), m.totalSleep)
        assertEquals(Duration.ofMinutes(15), m.timeToFallAsleep)
        assertTrue(SleepScore.of(m, Regularity(1.0, 1.0, 1.0, null))!!.deepRemRedistributed)
    }
}
