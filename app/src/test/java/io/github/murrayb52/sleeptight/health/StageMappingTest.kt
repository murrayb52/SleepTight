package io.github.murrayb52.sleeptight.health

import androidx.health.connect.client.records.SleepSessionRecord
import io.github.murrayb52.sleeptight.metrics.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class StageMappingTest {
    @Test
    fun `awake, awake in bed and out of bed are all awake`() {
        listOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
            .forEach { assertEquals(Stage.AWAKE, stageOf(it)) }
    }

    @Test
    fun `light, deep and REM map across`() {
        assertEquals(Stage.LIGHT, stageOf(SleepSessionRecord.STAGE_TYPE_LIGHT))
        assertEquals(Stage.DEEP, stageOf(SleepSessionRecord.STAGE_TYPE_DEEP))
        assertEquals(Stage.REM, stageOf(SleepSessionRecord.STAGE_TYPE_REM))
    }

    @Test
    fun `plain sleeping and unknown are asleep with no stage`() {
        assertEquals(Stage.UNKNOWN, stageOf(SleepSessionRecord.STAGE_TYPE_SLEEPING))
        assertEquals(Stage.UNKNOWN, stageOf(SleepSessionRecord.STAGE_TYPE_UNKNOWN))
        assertEquals(Stage.UNKNOWN, stageOf(99))
    }
}
