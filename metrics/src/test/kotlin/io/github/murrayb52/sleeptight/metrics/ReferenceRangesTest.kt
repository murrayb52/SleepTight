package io.github.murrayb52.sleeptight.metrics

import io.github.murrayb52.sleeptight.metrics.RangeVerdict.ABOVE
import io.github.murrayb52.sleeptight.metrics.RangeVerdict.BELOW
import io.github.murrayb52.sleeptight.metrics.RangeVerdict.WITHIN
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceRangesTest {
    @Test
    fun `total sleep is healthy from 7 to 9 hours inclusive`() {
        assertEquals(BELOW, ReferenceRanges.totalSleep(minutes(419)))
        assertEquals(WITHIN, ReferenceRanges.totalSleep(minutes(420)))
        assertEquals(WITHIN, ReferenceRanges.totalSleep(minutes(540)))
        assertEquals(ABOVE, ReferenceRanges.totalSleep(minutes(541)))
    }

    @Test
    fun `efficiency of 85 percent or more is healthy`() {
        assertEquals(BELOW, ReferenceRanges.efficiency(0.849))
        assertEquals(WITHIN, ReferenceRanges.efficiency(0.85))
        assertEquals(WITHIN, ReferenceRanges.efficiency(1.0))
    }

    @Test
    fun `falling asleep within 30 minutes is healthy`() {
        assertEquals(WITHIN, ReferenceRanges.timeToFallAsleep(minutes(0)))
        assertEquals(WITHIN, ReferenceRanges.timeToFallAsleep(minutes(30)))
        assertEquals(ABOVE, ReferenceRanges.timeToFallAsleep(minutes(31)))
    }

    @Test
    fun `deep and REM shares have their own bands`() {
        assertEquals(BELOW, ReferenceRanges.deep(0.12))
        assertEquals(WITHIN, ReferenceRanges.deep(0.13))
        assertEquals(WITHIN, ReferenceRanges.deep(0.23))
        assertEquals(ABOVE, ReferenceRanges.deep(0.24))
        assertEquals(BELOW, ReferenceRanges.rem(0.19))
        assertEquals(WITHIN, ReferenceRanges.rem(0.22))
        assertEquals(ABOVE, ReferenceRanges.rem(0.26))
    }
}
