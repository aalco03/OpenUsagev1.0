package com.openusage.app.activites

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * HealthConnectStepReader
 *
 * Java-friendly bridge to Android Health Connect for reading historical step counts.
 *
 * Health Connect's API is Kotlin coroutine-only; this object exposes blocking @JvmStatic
 * methods intended to be called from a BACKGROUND thread (the PASSIVE capture already runs
 * on its own thread). All Health Connect access is guarded behind API 26+ and SDK-availability
 * checks so the app remains safe on older devices (minSdk 23).
 *
 * History note: the Android on-device step recorder only accumulates data once an app holds
 * READ_STEPS, and reading beyond 30 days requires READ_HEALTH_DATA_HISTORY.
 */
object HealthConnectStepReader {

    private const val TAG = "HCStepReader"

    // Raw permission string for reading data older than 30 days.
    private const val PERMISSION_READ_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")

    /** Result row for a single step aggregation bucket. */
    data class StepBucket(
        val granularity: String,     // "daily" | "hourly"
        val periodLabel: String,
        val periodStartMs: Long,
        val periodEndMs: Long,
        val stepCount: Long,
        val dataOrigin: String
    )

    /**
     * Raw SDK status code (HealthConnectClient.SDK_AVAILABLE etc.), or -1 on API < 26.
     */
    @JvmStatic
    fun getSdkStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return -1
        return try {
            HealthConnectClient.getSdkStatus(context)
        } catch (e: Throwable) {
            Log.e(TAG, "getSdkStatus failed: ${e.message}", e)
            -1
        }
    }

    /** True only when Health Connect is installed/available and usable on this device. */
    @JvmStatic
    fun isAvailable(context: Context): Boolean {
        return getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /** True when the provider exists but needs a Play Store update/install (Android 8-13 case). */
    @JvmStatic
    fun isProviderUpdateRequired(context: Context): Boolean {
        return getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
    }

    /** The set of Health Connect permissions this feature needs (steps + history). */
    @JvmStatic
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            PERMISSION_READ_HISTORY
        )
    }

    /** Blocking check that all required permissions are granted. Call off the main thread. */
    @JvmStatic
    fun hasAllPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = runBlocking { client.permissionController.getGrantedPermissions() }
            val steps = HealthPermission.getReadPermission(StepsRecord::class)
            granted.contains(steps)
        } catch (e: Throwable) {
            Log.e(TAG, "hasAllPermissions failed: ${e.message}", e)
            false
        }
    }

    /**
     * Read daily step totals between [startMillis, endMillis). Blocking; call off main thread.
     * Returns an empty list on any failure or when unavailable.
     */
    @JvmStatic
    fun readDailySteps(context: Context, startMillis: Long, endMillis: Long): List<StepBucket> {
        if (!isAvailable(context)) return emptyList()
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zone)
            val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone)

            val results = runBlocking {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        timeRangeSlicer = Period.ofDays(1)
                    )
                )
            }

            val buckets = ArrayList<StepBucket>()
            for (group in results) {
                val count = group.result[StepsRecord.COUNT_TOTAL] ?: continue
                if (count <= 0) continue
                val bucketStart = group.startTime.atZone(zone).toInstant().toEpochMilli()
                val bucketEnd = group.endTime.atZone(zone).toInstant().toEpochMilli()
                buckets.add(
                    StepBucket(
                        granularity = "daily",
                        periodLabel = group.startTime.toLocalDate().format(DAY_FORMAT),
                        periodStartMs = bucketStart,
                        periodEndMs = bucketEnd,
                        stepCount = count,
                        dataOrigin = originsToString(group.result)
                    )
                )
            }
            Log.i(TAG, "readDailySteps: ${buckets.size} non-empty day buckets")
            buckets
        } catch (e: Throwable) {
            Log.e(TAG, "readDailySteps failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Read hourly step totals between [startMillis, endMillis). Blocking; call off main thread.
     * Returns an empty list on any failure or when unavailable.
     */
    @JvmStatic
    fun readHourlySteps(context: Context, startMillis: Long, endMillis: Long): List<StepBucket> {
        if (!isAvailable(context)) return emptyList()
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val startInstant = Instant.ofEpochMilli(startMillis)
            val endInstant = Instant.ofEpochMilli(endMillis)

            val results = runBlocking {
                client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                        timeRangeSlicer = Duration.ofHours(1)
                    )
                )
            }

            val buckets = ArrayList<StepBucket>()
            for (group in results) {
                val count = group.result[StepsRecord.COUNT_TOTAL] ?: continue
                if (count <= 0) continue
                val bucketStartMs = group.startTime.toEpochMilli()
                val bucketEndMs = group.endTime.toEpochMilli()
                val label = LocalDateTime.ofInstant(group.startTime, zone).format(HOUR_FORMAT)
                buckets.add(
                    StepBucket(
                        granularity = "hourly",
                        periodLabel = label,
                        periodStartMs = bucketStartMs,
                        periodEndMs = bucketEndMs,
                        stepCount = count,
                        dataOrigin = originsToString(group.result)
                    )
                )
            }
            Log.i(TAG, "readHourlySteps: ${buckets.size} non-empty hour buckets")
            buckets
        } catch (e: Throwable) {
            Log.e(TAG, "readHourlySteps failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun originsToString(result: AggregationResult): String {
        return try {
            result.dataOrigins.joinToString(",") { it.packageName }
        } catch (e: Throwable) {
            ""
        }
    }
}
