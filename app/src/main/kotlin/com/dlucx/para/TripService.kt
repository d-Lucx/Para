package com.dlucx.para

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

internal class TripService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val locations by lazy { getSystemService(LocationManager::class.java) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val settings by lazy { getSharedPreferences("para_settings", MODE_PRIVATE) }
    private val viewTrip by lazy { activityPendingIntent(ACTION_VIEW_TRIP, 1) }
    private val confirmEnd by lazy { activityPendingIntent(ACTION_CONFIRM_END_TRIP, 2) }
    private val showArrival by lazy { activityPendingIntent(null, 3, ArrivalActivity::class.java) }
    private var session: TripSession? = null
    private val history by lazy { TripHistory(this) }
    private var recordId = ""
    private var lastCheckpoint = 0L
    private var player: MediaPlayer? = null
    private var backupWakeLock: PowerManager.WakeLock? = null
    private var backupDeadline: Long? = null
    private var lastNotification: TripNotification? = null
    private var currentAlert: TripAlert? = null
    private var foreground = false

    private val screenState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = publish()
    }

    private val tick = object : Runnable {
        override fun run() {
            val trip = session ?: return
            trip.onTime(SystemClock.elapsedRealtime())
            if (SystemClock.elapsedRealtime() - lastCheckpoint >= 30_000L) checkpoint(interrupted = true)
            publish()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val trip = session ?: return
            val playApproach = trip.onFix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.NaN,
                fixMillis = location.elapsedRealtimeNanos / 1_000_000L,
                nowMillis = SystemClock.elapsedRealtime(),
            )
            publish(playApproach)
        }

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Required on Android versions before API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(TrackingChannel, getString(R.string.trip_tracking_channel), NotificationManager.IMPORTANCE_LOW)
        )
        ContextCompat.registerReceiver(this, screenState, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        notifications.createNotificationChannel(
            NotificationChannel(AlarmChannel, getString(R.string.trip_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            StartAction -> if (session == null) {
                val destination = intent.destination()
                if (destination == null) {
                    stopSelf(startId)
                } else {
                    begin(destination)
                }
            }
            KeepTrackingAction -> {
                val trip = session
                if (trip == null) {
                    stopSelf(startId)
                } else {
                    trip.dismissBackup(SystemClock.elapsedRealtime())
                    publish()
                }
            }
            EndAction -> {
                checkpoint(interrupted = false)
                stopSelf()
            }
            else -> if (session == null) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun begin(destination: DestinationDraft) {
        val now = SystemClock.elapsedRealtime()
        val trip = TripSession(destination, now)
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Precise location permission is required")
            }
            val snapshot = trip.snapshot()
            val initial = snapshot.notificationContent()
            val notification = trackingNotification(initial)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(TrackingNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(TrackingNotificationId, notification)
            }
            foreground = true
            lastNotification = initial
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
            destination.backupMinutes?.let { minutes ->
                val duration = minutes * 60_000L
                backupDeadline = now + duration
                backupWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Para:backupTimer")
                    .apply {
                        setReferenceCounted(false)
                        acquire(duration + 5_000L)
                    }
            }
            session = trip
            recordId = TripHistory.newId()
            checkpoint(interrupted = true)
            publish()
            handler.post(tick)
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.trip_start_failed, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun checkpoint(interrupted: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val summary = session?.finish(now) ?: return
        lastCheckpoint = now
        runCatching {
            history.checkpoint(
                TripRecord(recordId, if (interrupted) summary.copy(outcome = TripOutcome.Interrupted) else summary),
            )
        }.onFailure {
            if (!interrupted) Toast.makeText(this, "Couldn’t write the trip overview.", Toast.LENGTH_LONG).show()
        }
    }

    private fun publish(playApproach: Boolean = false) {
        val snapshot = session?.snapshot() ?: return
        val alert = snapshot.alert
        mutableState.value = snapshot
        val previous = currentAlert
        val content = snapshot.notificationContent()
        if (alert != previous) {
            currentAlert = alert
            when (alert) {
                TripAlert.Backup -> {
                    playSound(R.raw.backup_alarm, loop = true)
                    notifications.notify(AlarmNotificationId, alarmNotification(content, fullScreen = false))
                }
                TripAlert.Arrival -> {
                    playSound(R.raw.arrival_alarm, loop = true)
                    if (previous == TripAlert.Backup) notifications.cancel(AlarmNotificationId)
                    val allowFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                        notifications.canUseFullScreenIntent()
                    try {
                        notifications.notify(AlarmNotificationId, alarmNotification(content, allowFullScreen))
                    } catch (_: SecurityException) {
                        notifications.notify(AlarmNotificationId, alarmNotification(content, fullScreen = false))
                    }
                }
                null -> {
                    releasePlayer()
                    notifications.cancel(AlarmNotificationId)
                }
            }
        } else if (alert == null && playApproach) {
            playSound(R.raw.approach_chime, loop = false)
        } else if (alert != null && content != lastNotification) {
            notifications.notify(AlarmNotificationId, alarmNotification(content, fullScreen = false))
        }

        val deadline = backupDeadline
        if (deadline != null && (alert != null || SystemClock.elapsedRealtime() >= deadline)) {
            releaseBackupWakeLock()
        }
        if (content != lastNotification) {
            lastNotification = content
            notifications.notify(TrackingNotificationId, trackingNotification(content))
        }
    }

    private fun playSound(@RawRes resource: Int, loop: Boolean) {
        releasePlayer()
        try {
            val next = MediaPlayer.create(
                this,
                resource,
                AlarmAudio,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            ) ?: return
            player = next
            next.isLooping = loop
            if (loop) next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            next.setOnCompletionListener { if (it === player) releasePlayer() }
            next.setOnErrorListener { failed, _, _ ->
                if (failed === player) releasePlayer()
                true
            }
            next.start()
        } catch (_: RuntimeException) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun releaseBackupWakeLock() {
        backupWakeLock?.let { if (it.isHeld) it.release() }
        backupWakeLock = null
        backupDeadline = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(screenState)
        locations.removeUpdates(listener)
        releaseBackupWakeLock()
        releasePlayer()
        notifications.cancel(AlarmNotificationId)
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        session = null
        mutableState.value = null
        super.onDestroy()
    }

    private data class TripNotification(
        val status: String,
        val detail: String?,
        val publicDetails: Boolean,
    )

    private fun TripSnapshot.notificationContent(): TripNotification {
        val statusId = when (alert) {
            TripAlert.Backup -> R.string.trip_backup_notification
            TripAlert.Arrival -> R.string.trip_arrival_notification
            null -> when (status) {
                TripStatus.WaitingForGps -> R.string.trip_gps_waiting
                TripStatus.Tracking -> R.string.trip_tracking_notification
                TripStatus.Approaching -> R.string.trip_approaching_notification
                TripStatus.GpsSignalLost -> R.string.trip_gps_lost
            }
        }
        val distance = remainingMeters?.let { meters ->
            if (meters >= 1_000) getString(R.string.trip_distance_km, meters / 1_000)
            else getString(R.string.trip_distance_m, meters.roundToInt())
        }
        val publicDetails = settings.getBoolean(ShowLockScreenDetails, false)
        val detail = when {
            !publicDetails && (!power.isInteractive || keyguard.isKeyguardLocked) -> null
            distance == null -> destination.name
            else -> getString(R.string.trip_destination_distance, destination.name, distance)
        }
        return TripNotification(
            getString(statusId),
            detail,
            publicDetails,
        )
    }

    private fun baseNotification(channel: String, title: String) = Notification.Builder(this, channel)
        .setSmallIcon(R.drawable.ic_trip_notification)
        .setContentTitle(title)
        .setShowWhen(false)
        .setOngoing(true)
        .setContentIntent(viewTrip)
        .addAction(Notification.Action.Builder(null, getString(R.string.trip_notification_view), viewTrip).build())
        .addAction(Notification.Action.Builder(null, getString(R.string.trip_notification_end), confirmEnd).build())

    private fun detailedNotification(channel: String, content: TripNotification): Notification.Builder {
        val builder = baseNotification(channel, content.status).setContentText(content.detail)
        if (content.publicDetails) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
        } else {
            builder.setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(baseNotification(channel, content.status)
                    .setVisibility(Notification.VISIBILITY_PUBLIC).build())
        }
        return builder
    }

    private fun trackingNotification(content: TripNotification): Notification {
        val builder = detailedNotification(TrackingChannel, content)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun alarmNotification(content: TripNotification, fullScreen: Boolean): Notification {
        val builder = detailedNotification(AlarmChannel, content)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOnlyAlertOnce(!fullScreen)
        if (fullScreen) builder.setFullScreenIntent(showArrival, true)
        return builder.build()
    }

    private fun activityPendingIntent(
        action: String?,
        requestCode: Int,
        activity: Class<*> = MainActivity::class.java,
    ): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, activity)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun Intent.destination(): DestinationDraft? {
        val draft = DestinationDraft(
            name = getStringExtra(NameExtra).orEmpty(),
            latitude = getDoubleExtra(LatitudeExtra, Double.NaN),
            longitude = getDoubleExtra(LongitudeExtra, Double.NaN),
            approachMeters = getIntExtra(ApproachExtra, 0),
            arrivalMeters = getIntExtra(ArrivalExtra, 0),
            backupMinutes = getIntExtra(BackupExtra, 0).takeUnless { it == 0 },
        )
        return draft.takeIf { it.isValidTrip() }
    }

    companion object {
        const val ACTION_VIEW_TRIP = "com.dlucx.para.VIEW_TRIP"
        const val ACTION_CONFIRM_END_TRIP = "com.dlucx.para.CONFIRM_END_TRIP"
        private const val StartAction = "com.dlucx.para.START_TRIP"
        private const val KeepTrackingAction = "com.dlucx.para.KEEP_TRACKING"
        private const val EndAction = "com.dlucx.para.END_TRIP"
        internal const val TrackingChannel = "para_trip_tracking"
        internal const val AlarmChannel = "para_trip_alarm"
        private const val TrackingNotificationId = 101
        private const val AlarmNotificationId = 102
        private const val NameExtra = "destination_name"
        private const val LatitudeExtra = "destination_latitude"
        private const val LongitudeExtra = "destination_longitude"
        private const val ApproachExtra = "approach_meters"
        private const val ArrivalExtra = "arrival_meters"
        private const val BackupExtra = "backup_minutes"

        internal val AlarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private val mutableState = MutableStateFlow<TripSnapshot?>(null)
        val state = mutableState.asStateFlow()

        fun start(context: Context, destination: DestinationDraft): Boolean {
            if (state.value != null) return false
            if (!destination.isValidTrip()) {
                val message = if (destination.backupMinutes?.let { it !in 1..MaxBackupMinutes } == true) {
                    R.string.trip_backup_invalid
                } else {
                    R.string.trip_start_failed
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return false
            }
            return try {
                context.startForegroundService(Intent(context, TripService::class.java)
                    .setAction(StartAction)
                    .putExtra(NameExtra, destination.name)
                    .putExtra(LatitudeExtra, destination.latitude)
                    .putExtra(LongitudeExtra, destination.longitude)
                    .putExtra(ApproachExtra, destination.approachMeters)
                    .putExtra(ArrivalExtra, destination.arrivalMeters)
                    .putExtra(BackupExtra, destination.backupMinutes ?: 0))
                true
            } catch (_: RuntimeException) {
                Toast.makeText(context, R.string.trip_start_failed, Toast.LENGTH_LONG).show()
                false
            }
        }

        fun end(context: Context) {
            if (state.value != null) {
                context.startService(Intent(context, TripService::class.java).setAction(EndAction))
            }
        }

        fun keepTracking(context: Context) {
            if (state.value?.alert == TripAlert.Backup) {
                context.startService(Intent(context, TripService::class.java).setAction(KeepTrackingAction))
            }
        }

        private fun DestinationDraft.isValidTrip(): Boolean =
            name.isNotBlank() && CorridorPoint(latitude, longitude).isInsideCorridorCoverage() &&
                approachMeters > arrivalMeters && arrivalMeters > 0 &&
                (backupMinutes == null || backupMinutes in 1..MaxBackupMinutes)
    }
}
