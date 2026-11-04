package io.github.murrayb52.sleeptight.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.murrayb52.sleeptight.metrics.Stage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * The real Health Connect read path, on the emulator. The test writes sleep and heart rate as
 * SleepTight itself (debug builds have write permission), then reads it back with the source
 * pointed at SleepTight's own package, and checks the default Garmin-only source ignores it.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var client: HealthConnectClient
    private val bed = Instant.parse("2026-10-20T20:00:00Z")
    private val wake = bed.plus(Duration.ofHours(8))

    @Before
    fun setUp() {
        assumeTrue("Health Connect isn't available here", HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
        ).forEach { automation.grantRuntimePermission(context.packageName, it) }
        client = HealthConnectClient.getOrCreate(context)
    }

    @After
    fun tearDown() = runTest {
        if (::client.isInitialized) {
            client.deleteRecords(SleepSessionRecord::class, TimeRangeFilter.between(bed.minusSeconds(3600), wake.plusSeconds(3600)))
            client.deleteRecords(HeartRateRecord::class, TimeRangeFilter.between(bed.minusSeconds(3600), wake.plusSeconds(3600)))
        }
    }

    @Test
    fun readsStagesAndHeartRateAndFiltersByWhoWroteThem() = runTest {
        client.insertRecords(
            listOf(
                SleepSessionRecord(
                    startTime = bed.plusSeconds(600),
                    startZoneOffset = null,
                    endTime = wake,
                    endZoneOffset = null,
                    metadata = Metadata.manualEntry(),
                    stages = listOf(
                        SleepSessionRecord.Stage(bed.plusSeconds(600), bed.plusSeconds(3 * 3600), SleepSessionRecord.STAGE_TYPE_LIGHT),
                        SleepSessionRecord.Stage(bed.plusSeconds(3 * 3600), bed.plusSeconds(5 * 3600), SleepSessionRecord.STAGE_TYPE_DEEP),
                        SleepSessionRecord.Stage(bed.plusSeconds(5 * 3600), wake, SleepSessionRecord.STAGE_TYPE_REM),
                    ),
                ),
                HeartRateRecord(
                    startTime = bed.plusSeconds(3600),
                    startZoneOffset = null,
                    endTime = bed.plusSeconds(3660),
                    endZoneOffset = null,
                    samples = listOf(HeartRateRecord.Sample(bed.plusSeconds(3600), 54)),
                    metadata = Metadata.manualEntry(),
                ),
            ),
        )

        val ours = HealthConnectGarminSource(context, origins = setOf(context.packageName))
        assertEquals(SourceStatus.READY, ours.status())
        val segments = ours.sleep(bed, wake)
        assertEquals(listOf(Stage.LIGHT, Stage.DEEP, Stage.REM), segments.map { it.stage })
        assertEquals(listOf(54), ours.heartRate(bed, wake).map { it.bpm })

        // The real source only accepts Garmin Connect, so data written by anyone else is ignored.
        assertTrue(HealthConnectGarminSource(context).sleep(bed, wake).isEmpty())
    }
}
