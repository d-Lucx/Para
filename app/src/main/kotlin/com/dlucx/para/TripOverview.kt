package com.dlucx.para

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun TripOutcome.label(): String = when (this) {
    TripOutcome.ArrivalAlarm -> "Arrival alarm"
    TripOutcome.BackupAlert -> "Backup alert"
    TripOutcome.ManualStop -> "Manual stop"
    TripOutcome.Interrupted -> "Interrupted"
}

@Composable
internal fun TripOverview(
    record: TripRecord,
    unsaved: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val trip = record.summary
    var confirmDiscard by rememberSaveable(record.id) { mutableStateOf(false) }
    fun goBack() {
        if (busy) return
        if (unsaved) confirmDiscard = true else onClose()
    }
    BackHandler(onBack = ::goBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ParaHeader("Trip overview", badge = if (unsaved) "UNSAVED" else "LOCAL", onBack = ::goBack) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (unsaved) {
                        OutlinedButton(::goBack, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy) {
                            Text("Discard")
                        }
                        Button(onSave, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy) { Text("Save trip") }
                    } else {
                        OutlinedButton(::goBack, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
                            Text("Back to history")
                        }
                    }
                }
            }
        },
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ParaPanel {
                ParaBadge(trip.outcome.label(), highlighted = true)
                Text(trip.destination.name, style = MaterialTheme.typography.headlineLarge)
                if (trip.outcome == TripOutcome.Interrupted) Text("Showing the last recorded checkpoint.")
            }
            Text("Recorded path", style = MaterialTheme.typography.titleLarge)
            CorridorMap(
                selectedPoint = null,
                approachRadiusMeters = 0,
                arrivalRadiusMeters = 0,
                onPointSelected = {},
                onOutsideCoverage = {},
                modifier = Modifier.fillMaxWidth().height(300.dp),
                recordedSegments = trip.segments,
                readOnly = true,
            )
            Text(
                if (trip.segments.isEmpty()) "No GPS points were recorded."
                else "The map shows recorded GPS points. Gaps are excluded from distance.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParaMetric("Recorded distance", formatDistance(trip.recordedDistanceMeters), Modifier.weight(1f))
                ParaMetric("Elapsed time", formatElapsed(trip.elapsedMillis), Modifier.weight(1f))
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard unsaved trip?") },
            text = { Text("The recorded path and trip details will be deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard trip") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep reviewing") }
            },
        )
    }
}
