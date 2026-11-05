package com.dlucx.para

import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page(@StringRes val label: Int, val icon: Int) {
    Home(R.string.home, R.drawable.ic_para_home),
    History(R.string.history, R.drawable.ic_para_history),
    Settings(R.string.settings, R.drawable.ic_para_settings),
}

internal enum class Appearance(@StringRes val label: Int) {
    System(R.string.appearance_system),
    Light(R.string.appearance_light),
    Dark(R.string.appearance_dark),
}

private const val AppearanceKey = "appearance"

internal fun SharedPreferences.readAppearance(): Appearance {
    val stored = getString(AppearanceKey, null)
    return Appearance.entries.firstOrNull { it.name == stored } ?: Appearance.System
}

@Composable
internal fun ParaTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7AD6D3), onPrimary = Color(0xFF003736),
            primaryContainer = Color(0xFF00504E), onPrimaryContainer = Color(0xFFABFFFC),
            background = Color(0xFF121316), onBackground = Color(0xFFE3E2E6),
            surface = Color(0xFF1B1C20), onSurface = Color(0xFFE3E2E6),
            surfaceContainer = Color(0xFF22252A), surfaceContainerHigh = Color(0xFF292A2D),
            onSurfaceVariant = Color(0xFFBDC9C8), outline = Color(0xFF879392), outlineVariant = Color(0xFF3E4948),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF5A151A), onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006A68), onPrimary = Color.White,
            primaryContainer = Color(0xFFD6F1EE), onPrimaryContainer = Color(0xFF004F4D),
            background = Color(0xFFF8F9FA), onBackground = Color(0xFF171B20),
            surface = Color.White, onSurface = Color(0xFF171B20),
            surfaceContainer = Color(0xFFEFF2F9), surfaceContainerHigh = Color(0xFFE8ECF2),
            onSurfaceVariant = Color(0xFF414B4B), outline = Color(0xFF6E7B7A), outlineVariant = Color(0xFFCDD7D6),
            error = Color(0xFFB4232B), onError = Color.White,
            errorContainer = Color(0xFFFEE8E8), onErrorContainer = Color(0xFF861B23),
        )
    }
    val base = Typography()
    val window = LocalActivity.current?.window
    SideEffect {
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontFamily = ParaInter, fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontFamily = ParaInter, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = ParaInter, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontFamily = ParaInter, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontFamily = ParaInter),
            bodyLarge = base.bodyLarge.copy(fontFamily = ParaInter),
            bodyMedium = base.bodyMedium.copy(fontFamily = ParaInter),
            bodySmall = base.bodySmall.copy(fontFamily = ParaInter),
            labelLarge = base.labelLarge.copy(fontFamily = ParaInter),
            labelMedium = base.labelMedium.copy(fontFamily = ParaInter),
            labelSmall = base.labelSmall.copy(fontFamily = ParaInter),
        ),
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp)),
        content = content,
    )
}

@Composable
internal fun ParaApp(
    preferences: SharedPreferences,
    destinations: SavedDestinations,
    onTestAlarm: () -> Unit,
    trip: TripSnapshot?,
    permissions: TripPermissions,
    notificationEndRequested: Boolean,
    onNotificationEndHandled: () -> Unit,
    onStartTrip: (DestinationDraft) -> Unit,
    onEndTrip: () -> Unit,
    onKeepTracking: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<List<SavedDestination>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var writing by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<SavedDestination?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember(context) { TripHistory(context) }
    var pendingTrip by remember { mutableStateOf<TripRecord?>(null) }
    var savedTrips by remember { mutableStateOf<List<TripRecord>>(emptyList()) }
    var openedTrip by remember { mutableStateOf<TripRecord?>(null) }
    var deleteTrip by remember { mutableStateOf<TripRecord?>(null) }
    var historyError by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(true) }
    LaunchedEffect(trip != null) {
        if (trip == null) {
            historyLoading = true
            withContext(Dispatchers.IO) { runCatching { history.pending() to history.load() } }
                .onSuccess { (pending, records) ->
                    pendingTrip = pending
                    savedTrips = records
                    historyError = false
                }.onFailure { historyError = true }
            historyLoading = false
        }
    }
    LaunchedEffect(trip != null) {
        if (trip != null) {
            setupOpen = false
            page = Page.Home
            pendingDeletion = null
        }
    }
    LaunchedEffect(destinations, loadAttempt) {
        val result = withContext(Dispatchers.IO) { runCatching { destinations.load() } }
        saved = result.getOrNull()
        loadFailed = result.isFailure
    }

    fun closeSetup() {
        if (!writing) setupOpen = false
    }

    fun updateDestinations(closeOnSuccess: Boolean, operation: () -> List<SavedDestination>) {
        if (writing) return
        writing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(operation) }
            result.onSuccess {
                saved = it
                if (closeOnSuccess) setupOpen = false
            }.onFailure {
                Toast.makeText(context, R.string.destination_write_failed, Toast.LENGTH_LONG).show()
            }
            writing = false
        }
    }
    fun updateHistory(operation: () -> Unit, onSuccess: () -> Unit) {
        if (writing) return
        writing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(operation) }
            if (result.isSuccess) onSuccess() else {
                Toast.makeText(context, "Couldn’t update trip history.", Toast.LENGTH_LONG).show()
            }
            writing = false
        }
    }
    var appearance by remember(preferences) { mutableStateOf(preferences.readAppearance()) }

    BackHandler(enabled = trip == null && (setupOpen || page != Page.Home)) {
        if (setupOpen) closeSetup() else page = Page.Home
    }
    ParaTheme(appearance) {
        if (trip != null) {
            ActiveTrip(
                snapshot = trip,
                onEnd = onEndTrip,
                onKeepTracking = onKeepTracking,
                notificationEndRequested = notificationEndRequested,
                onNotificationEndHandled = onNotificationEndHandled,
            )
            return@ParaTheme
        }
        val startTrip = rememberTripStarter(preferences, onStartTrip)
        val overview = pendingTrip ?: openedTrip
        if (overview != null) {
            val unsaved = pendingTrip != null
            TripOverview(
                record = overview,
                unsaved = unsaved,
                busy = writing,
                onClose = {
                    if (unsaved) updateHistory(history::discard) { pendingTrip = null }
                    else openedTrip = null
                },
                onSave = {
                    updateHistory({ history.save(overview) }) {
                        savedTrips = listOf(overview) + savedTrips.filterNot { it.id == overview.id }
                        pendingTrip = null
                        page = Page.History
                    }
                },
            )
            return@ParaTheme
        }
        val entries = saved
        if (setupOpen && entries != null) {
            DestinationSetup(
                initialDestination = entries.firstOrNull { it.id == editingId }?.draft,
                onBack = ::closeSetup,
                onSave = { draft ->
                    val id = editingId
                    updateDestinations(closeOnSuccess = true) { destinations.save(id, draft) }
                },
                onTestAlarm = onTestAlarm,
                onStartTrip = { if (!writing && !historyLoading) startTrip(it) },
            )
            return@ParaTheme
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ParaHeader(
                    title = if (page == Page.Home) stringResource(R.string.app_name) else stringResource(page.label),
                    subtitle = when (page) {
                        Page.Home -> stringResource(R.string.commuter_alarm)
                        Page.History -> "On-device encrypted storage"
                        Page.Settings -> "Preferences & audio"
                    },
                    badge = if (page == Page.History) "LOCAL" else null,
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Page.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { page = destination },
                            icon = { ParaIcon(destination.icon) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            },
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    Page.Home -> {
                        ParaPanel {
                            ParaBadge("Map available offline", highlighted = true)
                            Text("Philippine regional maps", style = MaterialTheme.typography.titleLarge)
                            Text("Browse all 18 regions. Search towns, transit stops, and landmarks with streets and labels available offline.")
                        }
                        Button(
                            onClick = {
                                editingId = null
                                setupOpen = true
                            },
                            enabled = entries != null && !writing,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ParaIcon(R.drawable.ic_para_pin)
                                Text(stringResource(R.string.set_destination))
                            }
                        }
                        when {
                            loadFailed -> {
                                Text(stringResource(R.string.destination_load_failed))
                                TextButton(onClick = { loadAttempt++ }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                            entries == null -> Text(stringResource(R.string.loading_destinations))
                            entries.isEmpty() -> EmptyState(
                                R.string.saved_destinations,
                                R.string.no_saved_destinations,
                            )
                            else -> {
                                Text(
                                    stringResource(R.string.saved_destinations),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                entries.forEach { entry ->
                                    ParaPanel {
                                            ParaBadge("LOCAL DESTINATION")
                                            Text(entry.draft.name, style = MaterialTheme.typography.titleMedium)
                                            Text("Arrival radius · ${entry.draft.arrivalMeters} m")
                                            OutlinedButton(
                                                onClick = { startTrip(entry.draft) },
                                                enabled = !writing && !historyLoading,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                            ) { Text(stringResource(R.string.start_trip)) }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        editingId = entry.id
                                                        setupOpen = true
                                                    },
                                                    enabled = !writing,
                                                ) { Text(stringResource(R.string.edit_destination)) }
                                                TextButton(
                                                    onClick = { pendingDeletion = entry },
                                                    enabled = !writing,
                                                ) { Text(stringResource(R.string.remove_destination)) }
                                            }
                                    }
                                }
                            }
                        }
                    }
                    Page.History -> when {
                        historyError -> Text("Couldn’t open trip history.")
                        historyLoading -> Text("Opening trip history…")
                        savedTrips.isEmpty() -> EmptyState(R.string.no_saved_trips, R.string.saved_trips_help)
                        else -> savedTrips.forEach { record ->
                            ParaPanel {
                                    ParaBadge(record.summary.outcome.label(), highlighted = true)
                                    Text(record.summary.destination.name, style = MaterialTheme.typography.titleMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ParaMetric("Recorded distance", formatDistance(record.summary.recordedDistanceMeters), Modifier.weight(1f))
                                        ParaMetric("Elapsed", formatElapsed(record.summary.elapsedMillis), Modifier.weight(1f))
                                    }
                                    Row {
                                        TextButton(onClick = { openedTrip = record }, enabled = !writing) { Text("View trip") }
                                        TextButton(onClick = { deleteTrip = record }, enabled = !writing) { Text("Delete") }
                                    }
                            }
                        }
                    }
                    Page.Settings -> {
                        AppearanceOptions(appearance) { selected ->
                            appearance = selected
                            preferences.edit().putString(AppearanceKey, selected.name).apply()
                        }
                        TripSettings(preferences, permissions)
                        ParaPanel {
                            Text("Background tracking", style = MaterialTheme.typography.titleMedium)
                            Text(if (permissions.batteryOptimizationsIgnored) "Android battery optimization: exempt"
                                else "Android battery optimization: system-managed")
                            Text("Before a ride, open Battery settings, find Para, and allow background activity or choose Unrestricted if available. Names vary by phone.")
                            Text("On Infinix, also check Power Marathon or Phone Master for app power restrictions. Keep Para allowed to run in the background and test with the screen off.")
                            Text("Battery settings can help tracking continue, but do not guarantee alarm delivery. Use Test alarm before boarding.")
                            OutlinedButton(onClick = context::openParaBatterySettings,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open battery settings") }
                            TextButton(onClick = context::openParaAppSettings) { Text("Open Para app settings") }
                        }
                        ParaPanel {
                            Text("Alarm chime", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                onClick = onTestAlarm,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.test_alarm)) }
                        }
                        ParaPanel {
                            ParaBadge("OFFLINE VECTOR PACK")
                            Text("Philippines · 18 regional street packs", style = MaterialTheme.typography.titleMedium)
                            Text("Map and trip data stay on this device.")
                            MapCredits()
                        }
                    }
                }
            }
        }
        pendingDeletion?.let { entry ->
            AlertDialog(
                onDismissRequest = { pendingDeletion = null },
                title = { Text(stringResource(R.string.remove_destination_title)) },
                text = { Text(stringResource(R.string.remove_destination_message, entry.draft.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeletion = null
                        updateDestinations(closeOnSuccess = false) { destinations.delete(entry.id) }
                    }) { Text(stringResource(R.string.remove_destination)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeletion = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        deleteTrip?.let { record ->
            AlertDialog(
                onDismissRequest = { deleteTrip = null },
                title = { Text("Delete saved trip?") },
                text = { Text(record.summary.destination.name) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTrip = null
                        updateHistory({ history.delete(record) }) {
                            savedTrips = savedTrips.filterNot { it.id == record.id }
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteTrip = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun MapCredits() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Map sources and licenses") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Map sources and licenses") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Streets and landmarks: © OpenStreetMap contributors, ODbL 1.0. Extract supplied by Geofabrik.")
                    Text("openstreetmap.org/copyright\nopendatacommons.org/licenses/odbl/1-0/")
                    Text("Place names: GeoNames, CC BY 4.0.\ngeonames.org\ncreativecommons.org/licenses/by/4.0/")
                    Text("Regional boundaries: NAMRIA / Philippine Statistics Authority via OCHA, CC BY 3.0 IGO. PSGC updates: faeldon/philippines-psgc-shapefiles v1.0.0. Para simplified the boundaries.")
                    Text("data.humdata.org/dataset/cod-ab-phl\ncreativecommons.org/licenses/by/3.0/igo/")
                    Text("Map labels: Inter, © The Inter Project Authors, SIL Open Font License 1.1. Maps may omit roads, landmarks, or recent changes. A named town point is not an exact address or transit stop.")
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun TripSettings(preferences: SharedPreferences, permissions: TripPermissions) {
    val context = LocalContext.current
    var showDetails by remember(preferences) {
        mutableStateOf(preferences.getBoolean(ShowLockScreenDetails, false))
    }
    ParaPanel {
            Text(stringResource(R.string.trip_permissions), style = MaterialTheme.typography.titleMedium)
            PermissionButton(R.string.precise_location, permissions.preciseLocation, context::openParaAppSettings)
            PermissionButton(R.string.trip_notifications, permissions.notifications, context::openParaNotificationSettings)
            PermissionButton(R.string.lock_screen_alerts, permissions.lockScreenAlerts, context::openParaLockScreenSettings)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(showDetails, role = Role.Switch) {
                        showDetails = it
                        preferences.edit().putBoolean(ShowLockScreenDetails, it).apply()
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.show_lock_screen_details))
                    Text(
                        stringResource(R.string.lock_screen_privacy_help),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = showDetails, onCheckedChange = null)
            }
    }
}

@Composable
private fun PermissionButton(@StringRes label: Int, allowed: Boolean, openSettings: () -> Unit) {
    TextButton(onClick = openSettings, modifier = Modifier.heightIn(min = 48.dp)) {
        val status = if (allowed) R.string.permission_allowed else R.string.permission_disabled
        Text(stringResource(R.string.permission_status, stringResource(label), stringResource(status)))
    }
}

@Composable
private fun EmptyState(@StringRes title: Int, @StringRes description: Int) {
    ParaPanel {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppearanceOptions(appearance: Appearance, onSelect: (Appearance) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ParaPanel {
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Appearance.entries.forEach { option ->
                val selected = appearance == option
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) colors.primaryContainer else colors.surfaceContainer,
                    contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.heightIn(min = 48.dp)
                            .selectable(selected, role = Role.RadioButton) { onSelect(option) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(option.label), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
