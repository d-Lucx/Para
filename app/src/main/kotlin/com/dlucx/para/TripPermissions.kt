package com.dlucx.para

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

internal data class TripPermissions(
    val preciseLocation: Boolean,
    val notifications: Boolean,
    val lockScreenAlerts: Boolean,
)

internal fun Context.readTripPermissions(): TripPermissions {
    val manager = getSystemService(NotificationManager::class.java)
    val trackingChannel = manager.getNotificationChannel(TripService.TrackingChannel)
    val alarmChannel = manager.getNotificationChannel(TripService.AlarmChannel)
    return TripPermissions(
        preciseLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED,
        notifications = manager.areNotificationsEnabled() &&
            trackingChannel?.importance != NotificationManager.IMPORTANCE_NONE,
        lockScreenAlerts = (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) &&
            (alarmChannel == null || alarmChannel.importance >= NotificationManager.IMPORTANCE_HIGH),
    )
}

internal fun Context.openParaAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

internal fun Context.openParaNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}

internal fun Context.openParaLockScreenSettings() {
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent()) {
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null)),
        )
    } else if (manager.getNotificationChannel(TripService.AlarmChannel) != null) {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, TripService.AlarmChannel),
        )
    } else {
        openParaNotificationSettings()
    }
}

private enum class TripBlock(val explanation: String, val action: String) {
    Location("Precise location is required to check arrival.", "Open location permissions"),
    Notifications("Notifications for Para are off.", "Open Para notifications"),
    Tracking("The ongoing trip notification channel is blocked.", "Open tracking channel"),
    Alarm("The alarm notification channel is blocked.", "Open alarm channel"),
    AlarmPriority("The alarm notification channel is below high priority.", "Open alarm channel"),
    FullScreen("Android full-screen alert access for Para is off.", "Open full-screen access"),
}

private fun Context.tripBlocks(): List<TripBlock> {
    val manager = getSystemService(NotificationManager::class.java)
    return buildList {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            add(TripBlock.Location)
        }
        if (!manager.areNotificationsEnabled()) add(TripBlock.Notifications)
        if (manager.getNotificationChannel(TripService.TrackingChannel)?.importance == NotificationManager.IMPORTANCE_NONE) {
            add(TripBlock.Tracking)
        }
        val alarm = manager.getNotificationChannel(TripService.AlarmChannel)
        if (alarm?.importance == NotificationManager.IMPORTANCE_NONE) add(TripBlock.Alarm)
        else if (alarm != null && alarm.importance < NotificationManager.IMPORTANCE_HIGH) add(TripBlock.AlarmPriority)
        if (Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent()) add(TripBlock.FullScreen)
    }
}

private fun Context.fixIntent(block: TripBlock): Intent = when (block) {
    TripBlock.Location -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    TripBlock.Notifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    TripBlock.Tracking, TripBlock.Alarm, TripBlock.AlarmPriority -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, if (block == TripBlock.Tracking) TripService.TrackingChannel else TripService.AlarmChannel)
    TripBlock.FullScreen -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null))
}

@Composable
internal fun rememberTripStarter(
    preferences: SharedPreferences,
    onStart: (DestinationDraft) -> Unit,
): (DestinationDraft) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<DestinationDraft?>(null) }
    var blocks by remember { mutableStateOf<List<TripBlock>?>(null) }

    fun cancel() {
        blocks = null
        pending = null
    }

    fun start(acceptLimitedAlerts: Boolean = false) {
        val draft = pending ?: return
        val current = context.tripBlocks()
        if (TripBlock.Location in current || (!acceptLimitedAlerts && current.isNotEmpty())) {
            blocks = current
            return
        }
        cancel()
        onStart(draft)
    }

    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pending != null) blocks = context.tripBlocks()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    fun checkNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean(NotificationRequested, false)
        ) {
            preferences.edit().putBoolean(NotificationRequested, true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }

    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (TripBlock.Location in context.tripBlocks()) blocks = context.tripBlocks() else checkNotifications()
    }

    blocks?.let { current ->
        AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text(if (current.isEmpty()) "Ready to start" else "Trip permissions and alerts") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (current.isEmpty()) Text("Your trip settings are ready.")
                    current.forEach { block ->
                        Text(block.explanation)
                        TextButton(onClick = {
                            try {
                                settings.launch(context.fixIntent(block))
                            } catch (_: ActivityNotFoundException) {
                                settings.launch(context.fixIntent(TripBlock.Location))
                            }
                        }) { Text(block.action) }
                    }
                }
            },
            confirmButton = {
                if (TripBlock.Location !in current) {
                    TextButton(onClick = { start(acceptLimitedAlerts = current.isNotEmpty()) }) {
                        Text(if (current.isEmpty()) "Start trip" else stringResource(R.string.continue_trip_start))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = ::cancel) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    return { draft ->
        if (pending == null) {
            pending = draft
            if (TripBlock.Location !in context.tripBlocks()) checkNotifications() else {
                location.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ))
            }
        }
    }
}

private const val NotificationRequested = "trip_notifications_requested"
internal const val ShowLockScreenDetails = "show_lock_screen_details"

