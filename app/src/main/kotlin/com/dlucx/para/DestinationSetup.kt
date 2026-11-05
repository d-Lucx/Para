package com.dlucx.para

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class DestinationDraft(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val approachMeters: Int = 1500,
    val arrivalMeters: Int = 300,
    val backupMinutes: Int? = null,
)

@Composable
internal fun DestinationSetup(
    initialDestination: DestinationDraft?,
    onBack: () -> Unit,
    onSave: (DestinationDraft) -> Unit,
    onTestAlarm: () -> Unit,
    onStartTrip: (DestinationDraft) -> Unit,
) {
    val assets = LocalContext.current.assets
    val catalog = remember(assets) { StopCatalog.load(assets) }
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var point by remember(initialDestination) {
        mutableStateOf(initialDestination?.let { CorridorPoint(it.latitude, it.longitude) })
    }
    var name by remember(initialDestination) { mutableStateOf(initialDestination?.name.orEmpty()) }
    var approach by remember(initialDestination) {
        mutableStateOf((initialDestination?.approachMeters ?: 1500).toString())
    }
    var arrival by remember(initialDestination) {
        mutableStateOf((initialDestination?.arrivalMeters ?: 300).toString())
    }
    var backupEnabled by remember(initialDestination) {
        mutableStateOf(initialDestination?.backupMinutes != null)
    }
    var backupMinutes by remember(initialDestination) {
        mutableStateOf(initialDestination?.backupMinutes?.toString().orEmpty())
    }
    var outsideCoverage by remember { mutableStateOf(false) }

    val selectedPoint = point?.takeIf { it.isInsideCorridorCoverage() }
    val approachMeters = approach.toIntOrNull()?.takeIf { it > 0 }
    val arrivalMeters = arrival.toIntOrNull()?.takeIf { it > 0 }
    val orderedRings = approachMeters != null && arrivalMeters != null && approachMeters > arrivalMeters
    val backup = backupMinutes.toIntOrNull()?.takeIf { it in 1..MaxBackupMinutes }
    val results = remember(catalog, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            catalog.search(query).filter { stop ->
                CorridorPoint(stop.latitude, stop.longitude).isInsideCorridorCoverage()
            }.take(6)
        }
    }

    fun selectPoint(position: CorridorPoint, label: String) {
        point = position
        name = label
        outsideCoverage = false
        query = ""
        focus.clearFocus()
    }

    val draft = if (selectedPoint != null && name.isNotBlank() && orderedRings &&
        (!backupEnabled || backup != null)
    ) {
        DestinationDraft(
            name.trim(), selectedPoint.latitude, selectedPoint.longitude, approachMeters, arrivalMeters,
            backup.takeIf { backupEnabled },
        )
    } else null
    var coordinatesOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ParaHeader("Destination setup", badge = "OFFLINE", onBack = onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onTestAlarm, Modifier.weight(1f).heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) {
                        Text("Test alarm")
                    }
                    Button(
                        onClick = { draft?.let(onStartTrip) },
                        enabled = draft != null,
                        modifier = Modifier.weight(1.2f).heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("Start trip") }
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(128) },
                label = { Text("Search offline stops") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
            OutlinedButton(
                onClick = { focus.clearFocus(); coordinatesOpen = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Enter destination coordinates") }
            if (query.isNotBlank()) {
                if (results.isEmpty()) {
                    Text("No matching stops. You can still drop a pin on the map.")
                }
                results.forEach { stop ->
                    OutlinedButton(
                        onClick = {
                            selectPoint(CorridorPoint(stop.latitude, stop.longitude), stop.name)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(stop.name, style = MaterialTheme.typography.titleSmall)
                            Text(stop.locality, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    Text(
                        "Stop data © OpenStreetMap contributors · ODbL",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            ParaPanel {
                ParaBadge("ARRIVAL TARGET", highlighted = true)
                Text(
                    if (selectedPoint == null) "Choose a destination" else name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("Search offline stops or tap inside the Tagum–Davao boundary.")
            }
            CorridorMap(
                selectedPoint = selectedPoint,
                approachRadiusMeters = if (orderedRings) approachMeters else 0,
                arrivalRadiusMeters = if (orderedRings) arrivalMeters else 0,
                onPointSelected = { selectPoint(it, "Dropped pin") },
                onOutsideCoverage = { outsideCoverage = true },
                onApproachRadiusChanged = { meters ->
                    if (meters > (arrivalMeters ?: 0)) approach = meters.toString()
                },
                onArrivalRadiusChanged = { meters ->
                    if (meters > 0 && meters < (approachMeters ?: 0)) arrival = meters.toString()
                },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
            ParaPanel {
                Text("The first usable GPS fix can trigger the arrival alarm immediately if you are already inside the arrival zone.")
            }
            if (outsideCoverage) {
                Text(
                    "This point is outside offline coverage. Choose a destination inside the boundary.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (selectedPoint != null) {
                Text(
                    "Destination selected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text("Destination name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            Text("Alert zones", style = MaterialTheme.typography.titleMedium)
            Text("Drag a ring handle on the map or adjust its distance below.")
            RadiusPanel("Approach (m)", "PRE-ALERT", approach, { approach = it }, 100)
            RadiusPanel("Arrival (m)", "WAKE ALARM", arrival, { arrival = it }, 50)
            if (!orderedRings) {
                Text(
                    "Enter positive distances. Approach must be larger than arrival.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ParaPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Backup timer", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = backupEnabled,
                            onCheckedChange = { backupEnabled = it },
                            modifier = Modifier.semantics { contentDescription = "Backup timer" },
                        )
                    }
                    if (backupEnabled) {
                        NumberInput("Minutes after trip starts", backupMinutes, { backupMinutes = it }, backup == null)
                        if (backup == null) Text("Enter 1 to $MaxBackupMinutes minutes.")
                        Text("The timer begins when you start a trip, even while waiting for GPS.")
                    }
            }
            OutlinedButton(
                onClick = { draft?.let(onSave) },
                enabled = draft != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save destination")
            }
        }
    }
    if (coordinatesOpen) {
        CoordinateDialog(
            onDismiss = { coordinatesOpen = false },
            onSelect = {
                coordinatesOpen = false
                selectPoint(it, "Custom destination")
            },
        )
    }
}

@Composable
private fun CoordinateDialog(onDismiss: () -> Unit, onSelect: (CorridorPoint) -> Unit) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun parse(value: String) = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom destination") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter latitude and longitude in decimal degrees within the Tagum–Davao map area.")
                OutlinedTextField(
                    latitude, { latitude = it.take(32); error = null },
                    label = { Text("Latitude, for example 7.46") }, singleLine = true,
                )
                OutlinedTextField(
                    longitude, { longitude = it.take(32); error = null },
                    label = { Text("Longitude, for example 125.80") }, singleLine = true,
                )
                error?.let {
                    Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lat = parse(latitude)
                val lon = parse(longitude)
                if (lat == null || lon == null) {
                    error = "Enter valid decimal numbers for both coordinates."
                } else {
                    val point = CorridorPoint(lat, lon)
                    if (point.isInsideCorridorCoverage()) onSelect(point)
                    else error = "These coordinates are outside the Tagum–Davao map area."
                }
            }) { Text("Use coordinates") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RadiusPanel(label: String, badge: String, value: String, onChange: (String) -> Unit, step: Int) {
    val distance = value.toIntOrNull()
    ParaPanel {
        ParaBadge(badge)
        NumberInput(label, value, onChange, distance == null || distance <= 0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { distance?.let { onChange((it - step).coerceAtLeast(1).toString()) } },
                enabled = distance != null && distance > 1,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text("− $step m") }
            OutlinedButton(
                onClick = { distance?.let { onChange((it + step).toString()) } },
                enabled = distance != null && distance <= Int.MAX_VALUE - step,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text("+ $step m") }
        }
    }
}

internal const val MaxBackupMinutes = 1_440

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 10 && it.all { char -> char in '0'..'9' }) onChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
    )
}
