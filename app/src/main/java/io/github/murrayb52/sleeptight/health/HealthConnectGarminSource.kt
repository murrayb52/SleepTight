package io.github.murrayb52.sleeptight.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.murrayb52.sleeptight.metrics.SleepSegment
import io.github.murrayb52.sleeptight.metrics.Stage
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Reads Garmin's sleep sessions and heart rate from Health Connect, where Garmin Connect writes them.
 * Only records written by the apps in [origins] are read: by default just Garmin Connect, so sleep
 * that Samsung Health (or anything else) writes is ignored.
 */
class HealthConnectGarminSource(
    private val context: Context,
    private val origins: Set<String> = setOf(GARMIN_CONNECT),
) : GarminSource {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun status(): SourceStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> SourceStatus.UNAVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> SourceStatus.UPDATE_REQUIRED
        else -> if (client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) SourceStatus.READY else SourceStatus.NEEDS_PERMISSION
    }

    override suspend fun sleep(from: Instant, to: Instant): List<SleepSegment> =
        readAll(SleepSessionRecord::class, from, to).flatMap { it.toSegments() }

    override suspend fun heartRate(from: Instant, to: Instant): List<HeartRatePoint> =
        readAll(HeartRateRecord::class, from, to).flatMap { r -> r.samples.map { HeartRatePoint(it.time, it.beatsPerMinute.toInt()) } }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var page: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    dataOriginFilter = origins.map { DataOrigin(it) }.toSet(),
                    pageToken = page,
                ),
            )
            out += response.records
            page = response.pageToken
        } while (page != null)
        return out
    }

    companion object {
        const val GARMIN_CONNECT = "com.garmin.android.apps.connectmobile"

        /** What SleepTight asks Health Connect for: reading sleep and heart rate, nothing else. */
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
    }
}

/**
 * A sleep session as segments. Sessions with stages become one segment per stage; a session
 * without stages (just "asleep") becomes one segment of [Stage.UNKNOWN] (asleep, stage not known).
 */
fun SleepSessionRecord.toSegments(): List<SleepSegment> =
    if (stages.isEmpty()) {
        listOf(SleepSegment(startTime, endTime, Stage.UNKNOWN))
    } else {
        stages.map { SleepSegment(it.startTime, it.endTime, stageOf(it.stage)) }
    }

/** Health Connect's stage types mapped to SleepTight's. Out of bed and awake in bed are both awake. */
fun stageOf(type: Int): Stage = when (type) {
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
    -> Stage.AWAKE
    SleepSessionRecord.STAGE_TYPE_LIGHT -> Stage.LIGHT
    SleepSessionRecord.STAGE_TYPE_DEEP -> Stage.DEEP
    SleepSessionRecord.STAGE_TYPE_REM -> Stage.REM
    else -> Stage.UNKNOWN // STAGE_TYPE_SLEEPING and STAGE_TYPE_UNKNOWN: asleep, stage not known
}
