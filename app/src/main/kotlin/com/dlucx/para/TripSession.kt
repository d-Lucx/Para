package com.dlucx.para

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class TripStatus { WaitingForGps, Tracking, Approaching, GpsSignalLost }

internal enum class TripAlert { Backup, Arrival }

internal enum class TripOutcome { ArrivalAlarm, BackupAlert, ManualStop, Interrupted }

internal data class TripSnapshot(
    val destination: DestinationDraft,
    val status: TripStatus,
    val remainingMeters: Double?,
    val elapsedMillis: Long,
    val alert: TripAlert?,
    val recordedDistanceMeters: Double,
    val backupTimerActive: Boolean = false,
    val gpsProviderEnabled: Boolean = true,
)

internal data class TripPoint(val latitude: Double, val longitude: Double, val fixMillis: Long)

internal data class TripSummary(
    val destination: DestinationDraft,
    val elapsedMillis: Long,
    val recordedDistanceMeters: Double,
    val outcome: TripOutcome,
    val segments: List<List<TripPoint>>,
)

/** Trip rules for the service. All timestamps are from the same monotonic clock. */
internal class TripSession(val destination: DestinationDraft, private val startedAtMillis: Long) {
    private var observedMillis = startedAtMillis
    private var remainingMeters: Double? = null
    private var alert: TripAlert? = null
    private var approachChimed = false
    private var backupFired = false
    private val segments = mutableListOf<MutableList<TripPoint>>()
    private var recordedDistanceMeters = 0.0
    private val backupDelayMillis = destination.backupMinutes?.times(60_000L)

    private val lastPoint: TripPoint?
        get() = segments.lastOrNull()?.last()

    fun onTime(nowMillis: Long) {
        observedMillis = maxOf(observedMillis, nowMillis)
        val point = lastPoint
        if (point != null && observedMillis - point.fixMillis > MaxFixAgeMillis) {
            remainingMeters = null
        }
        if (backupDelayMillis != null && observedMillis - startedAtMillis >= backupDelayMillis &&
            !backupFired && alert != TripAlert.Arrival
        ) {
            backupFired = true
            alert = TripAlert.Backup
        }
    }

    /** Returns true when the service should play the one-time approach chime. */
    fun onFix(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        fixMillis: Long,
        nowMillis: Long,
    ): Boolean {
        onTime(nowMillis)
        val previous = lastPoint
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !(accuracyMeters > 0f && accuracyMeters <= MaxAccuracyMeters) ||
            fixMillis !in startedAtMillis..observedMillis ||
            observedMillis - fixMillis > MaxFixAgeMillis ||
            (previous != null && fixMillis <= previous.fixMillis)
        ) {
            return false
        }

        val point = TripPoint(latitude, longitude, fixMillis)
        if (previous == null || remainingMeters == null || fixMillis - previous.fixMillis > MaxFixAgeMillis) {
            segments.add(mutableListOf(point))
        } else {
            segments.last().add(point)
            recordedDistanceMeters += distanceMeters(previous.latitude, previous.longitude, latitude, longitude)
        }

        val distance = distanceMeters(latitude, longitude, destination.latitude, destination.longitude)
        remainingMeters = distance
        if (distance <= destination.arrivalMeters) {
            alert = TripAlert.Arrival
        }
        if (alert != null || approachChimed || distance > destination.approachMeters) return false

        approachChimed = true
        return true
    }

    fun dismissBackup(nowMillis: Long) {
        onTime(nowMillis)
        if (alert == TripAlert.Backup) alert = null
    }

    fun onGpsUnavailable() {
        remainingMeters = null
    }

    fun snapshot(): TripSnapshot {
        val distance = remainingMeters
        val status = when {
            lastPoint == null -> TripStatus.WaitingForGps
            distance == null -> TripStatus.GpsSignalLost
            distance <= destination.approachMeters -> TripStatus.Approaching
            else -> TripStatus.Tracking
        }
        return TripSnapshot(
            destination = destination,
            status = status,
            remainingMeters = distance,
            elapsedMillis = observedMillis - startedAtMillis,
            alert = alert,
            recordedDistanceMeters = recordedDistanceMeters,
            backupTimerActive = backupDelayMillis != null && !backupFired && alert != TripAlert.Arrival,
        )
    }

    fun finish(nowMillis: Long): TripSummary {
        val outcome = when (alert) {
            TripAlert.Arrival -> TripOutcome.ArrivalAlarm
            TripAlert.Backup -> TripOutcome.BackupAlert
            null -> TripOutcome.ManualStop
        }
        return TripSummary(
            destination = destination,
            elapsedMillis = maxOf(observedMillis, nowMillis) - startedAtMillis,
            recordedDistanceMeters = recordedDistanceMeters,
            outcome = outcome,
            segments = segments.map { it.toList() },
        )
    }

    private companion object {
        const val MaxFixAgeMillis = 15_000L
        const val MaxAccuracyMeters = 50f
        const val EarthRadiusMeters = 6_371_000.0

        fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
            val halfLatitude = Math.toRadians(toLat - fromLat) / 2
            val halfLongitude = Math.toRadians(toLon - fromLon) / 2
            val a = sin(halfLatitude) * sin(halfLatitude) +
                cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
                sin(halfLongitude) * sin(halfLongitude)
            return 2 * EarthRadiusMeters * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}
