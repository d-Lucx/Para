package com.dlucx.para

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun ActiveTrip(
    snapshot: TripSnapshot,
    onEnd: () -> Unit,
    onKeepTracking: () -> Unit,
    showDestinationDetails: Boolean = true,
    notificationEndRequested: Boolean = false,
    onNotificationEndHandled: () -> Unit = {},
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(notificationEndRequested) {
        if (notificationEndRequested) {
            confirmEnd = true
            onNotificationEndHandled()
        }
    }
    LaunchedEffect(snapshot.alert) {
        if (snapshot.alert != null) scroll.scrollTo(0)
    }

    val colors = MaterialTheme.colorScheme
    val warningColor = if (colors.surface.luminance() < 0.3f) Color(0xFFFFB84C) else Color(0xFF875000)
    val statusColor = when (snapshot.status) {
        TripStatus.Tracking -> colors.primary
        TripStatus.WaitingForGps, TripStatus.GpsSignalLost, TripStatus.Approaching -> warningColor
    }
    val arrival = snapshot.alert == TripAlert.Arrival

    Scaffold(
        containerColor = colors.background,
        topBar = { ParaHeader(if (arrival) "Arrival alarm" else "Active trip", badge = "OFFLINE") },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets)
                .verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (arrival) ArrivalBanner()
            if (!arrival && snapshot.alert == null) GpsStatus(snapshot.status, statusColor)
            if (showDestinationDetails) {
                ParaPanel {
                    ParaBadge("DESIGNATED STOP")
                    Text(snapshot.destination.name, style = MaterialTheme.typography.headlineLarge)
                    TripMetrics(snapshot)
                }
            }
            if (snapshot.alert == TripAlert.Backup) {
                BackupAlert(warningColor, onKeepTracking, onEnd = { confirmEnd = true })
            }
            if (arrival) {
                SlideToEnd(onEnd)
            }
            if (snapshot.alert != TripAlert.Backup) {
                Button(
                    onClick = { confirmEnd = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                ) {
                    Text("End trip")
                }
            }
        }
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End trip?") },
            text = { Text("This will stop location tracking and end your trip.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    onEnd()
                }) { Text("End trip") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ArrivalBanner() {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
        ),
    ) {
        Column(
            Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Arrival alarm", style = MaterialTheme.typography.headlineMedium)
            Text("You’re within the arrival zone.")
        }
    }
}

@Composable
private fun BackupAlert(color: Color, onKeepTracking: () -> Unit, onEnd: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Arrival not confirmed", style = MaterialTheme.typography.titleLarge, color = color)
                Text("Your backup timer has ended.")
            }
            Button(onKeepTracking, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Keep tracking")
            }
            OutlinedButton(onEnd, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("End trip")
            }
        }
    }
}

@Composable
private fun TripMetrics(snapshot: TripSnapshot) {
    ParaMetric(
        "Straight-line distance remaining",
        snapshot.remainingMeters?.let(::formatDistance) ?: "GPS fix needed",
        Modifier.fillMaxWidth(),
    )
    ParaMetric("Elapsed time", formatElapsed(snapshot.elapsedMillis), Modifier.fillMaxWidth())
}

@Composable
private fun GpsStatus(status: TripStatus, color: Color) {
    val (title, description) = when (status) {
        TripStatus.WaitingForGps -> "Waiting for GPS" to
            "Para will check your distance when it gets a usable fix."
        TripStatus.Tracking -> "Tracking" to "Checking your distance to the destination."
        TripStatus.GpsSignalLost -> "GPS signal lost" to
            "Para will resume proximity checks when GPS returns."
        TripStatus.Approaching -> "Approaching destination" to "You’re within the approach zone."
    }
    ParaPanel(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = color)
            Text(description)
    }
}

@Composable
private fun SlideToEnd(onEnd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val latestEnd by rememberUpdatedState(onEnd)
    var progress by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .background(colors.errorContainer, CircleShape)
            .clearAndSetSemantics {},
    ) {
        val travel = (maxWidth - 64.dp).coerceAtLeast(1.dp)
        val travelPx = with(density) { travel.toPx() }
        val thumbHitPx = with(density) { 64.dp.toPx() }
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .pointerInput(direction, travelPx, thumbHitPx) {
                    var draggingThumb = false
                    val sign = if (direction == LayoutDirection.Ltr) 1 else -1
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            val distanceFromStart = if (sign == 1) start.x else size.width - start.x
                            draggingThumb = distanceFromStart in 0f..thumbHitPx
                        },
                        onDragCancel = { progress = 0f },
                        onDragEnd = {
                            val completed = draggingThumb && progress >= 0.85f
                            progress = 0f
                            if (completed) latestEnd()
                        },
                    ) { _, dx ->
                        if (draggingThumb) {
                            progress = (progress + sign * dx / travelPx).coerceIn(0f, 1f)
                        }
                    }
                },
        ) {
            Text(
                "Slide to end trip",
                modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                    .padding(horizontal = 64.dp, vertical = 12.dp),
                color = colors.onErrorContainer,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 6.dp + travel * progress)
                    .size(52.dp).background(colors.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (direction == LayoutDirection.Ltr) "»" else "«",
                    color = colors.onError,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

internal fun formatDistance(meters: Double): String =
    if (meters < 1_000) "${meters.roundToLong()} m" else
        String.format(Locale.getDefault(), "%.1f km", meters / 1_000)

internal fun formatElapsed(millis: Long): String {
    val seconds = millis / 1_000
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds % 60}s"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
