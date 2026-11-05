package com.dlucx.para

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val mapData by rememberOfflineMapData()
    val loaded = mapData?.getOrNull()
    if (loaded == null) {
        BackHandler(onBack = onBack)
        Scaffold(topBar = { ParaHeader("Destination setup", onBack = onBack) }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                Text(if (mapData == null) "Opening offline maps…" else "Offline maps unavailable. Reopen destination setup to retry.")
            }
        }
        return
    }
    val (catalog, maps) = loaded
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
    var region by remember { mutableStateOf<MapRegion?>(null) }
    var regionsOpen by remember { mutableStateOf(false) }
    var focusSelection by remember(initialDestination) { mutableStateOf(initialDestination != null) }

    val selectedPoint = remember(point, maps) { point?.takeIf(maps::covers) }
    val selectedRegion = remember(selectedPoint, maps) { selectedPoint?.let(maps::regionAt) }
    val approachMeters = approach.toIntOrNull()?.takeIf { it > 0 }
    val arrivalMeters = arrival.toIntOrNull()?.takeIf { it > 0 }
    val orderedRings = approachMeters != null && arrivalMeters != null && approachMeters > arrivalMeters
    val backup = backupMinutes.toIntOrNull()?.takeIf { it in 1..MaxBackupMinutes }
    val results by produceState(emptyList<TransitStop>(), catalog, query) {
        value = if (query.isBlank()) emptyList() else withContext(Dispatchers.Default) {
            catalog.search(query).take(6)
        }
    }

    fun selectPoint(position: CorridorPoint, label: String, centerMap: Boolean = false) {
        point = position
        name = label
        focusSelection = centerMap
        if (centerMap) region = maps.regionAt(position)
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
        topBar = { ParaHeader("Destination setup", onBack = onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val radius = if (orderedRings) "Arrival radius: $arrivalMeters m" else "Check alert radii"
                    val timer = when {
                        !backupEnabled -> "Backup timer off"
                        backup == null -> "Enter a backup time"
                        else -> "Backup timer: $backup min"
                    }
                    Text("$radius · $timer", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(128) },
                label = { Text("Search places or stops") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
            OutlinedButton(
                onClick = { focus.clearFocus(); regionsOpen = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(region?.let { "Browse regions · ${it.name}" } ?: "Browse Philippine regions") }
            if (query.isNotBlank()) {
                if (results.isEmpty()) {
                    Text("No matching places. Try the town name or place a custom pin.")
                }
                results.forEach { stop ->
                    OutlinedButton(
                        onClick = {
                            selectPoint(CorridorPoint(stop.latitude, stop.longitude), stop.name, centerMap = true)
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
                        "Place data: GeoNames and OpenStreetMap",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            CorridorMap(
                selectedPoint = selectedPoint,
                approachRadiusMeters = if (orderedRings) approachMeters else 0,
                arrivalRadiusMeters = if (orderedRings) arrivalMeters else 0,
                onPointSelected = { position, label ->
                    val nearby = if (label == null) catalog.nearby(position) else null
                    selectPoint(position, label ?: nearby?.let { "Pin near ${it.name}" } ?: "Custom pin")
                },
                onOutsideCoverage = { outsideCoverage = true },
                onApproachRadiusChanged = { meters ->
                    if (meters > (arrivalMeters ?: 0)) approach = meters.toString()
                },
                onArrivalRadiusChanged = { meters ->
                    if (meters > 0 && meters < (approachMeters ?: 0)) arrival = meters.toString()
                },
                modifier = Modifier.fillMaxWidth().height(360.dp),
                region = region,
                focusSelection = focusSelection,
            )
            ParaPanel {
                ParaBadge("ARRIVAL TARGET", highlighted = true)
                Text(
                    if (selectedPoint == null) "Choose a destination" else name,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (selectedPoint == null) {
                    Text("Search for a town, terminal, airport, or port. Tap a map label to use that named point, or tap the map for a custom pin.")
                } else {
                    selectedRegion?.let { Text(it.name) }
                    Text(
                        "Offline streets and landmarks. Check the pin before starting.",
                    )
                }
            }
            OutlinedButton(
                onClick = { focus.clearFocus(); coordinatesOpen = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Enter destination coordinates") }
            ParaPanel {
                Text("The first usable GPS fix can trigger the arrival alarm immediately if you are already inside the arrival zone.")
            }
            if (outsideCoverage) {
                Text(
                    "This point is outside the bundled map areas. Choose a named place or a point inside a region.",
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
            maps = maps,
            onDismiss = { coordinatesOpen = false },
            onSelect = {
                coordinatesOpen = false
                selectPoint(it, "Custom destination", centerMap = true)
            },
        )
    }
    if (regionsOpen) {
        AlertDialog(
            onDismissRequest = { regionsOpen = false },
            title = { Text("Bundled Philippine regions") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { region = null; regionsOpen = false }) { Text("All Philippines") }
                    maps.regions.forEach { item ->
                        TextButton(onClick = { region = item; regionsOpen = false }) { Text(item.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { regionsOpen = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun CoordinateDialog(maps: PhilippineMaps, onDismiss: () -> Unit, onSelect: (CorridorPoint) -> Unit) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun parse(value: String) = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom destination") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter latitude and longitude in decimal degrees within a bundled Philippine map area.")
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
                    if (maps.covers(point)) onSelect(point)
                    else error = "These coordinates are outside the bundled map areas."
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
