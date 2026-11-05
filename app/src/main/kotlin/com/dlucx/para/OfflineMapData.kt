package com.dlucx.para

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class OfflineMapData(val catalog: StopCatalog, val maps: PhilippineMaps, val pilot: PilotMaps)

@Composable
internal fun rememberOfflineMapData(attempt: Int): State<Result<OfflineMapData>?> {
    val context = LocalContext.current.applicationContext
    return produceState<Result<OfflineMapData>?>(null, context, attempt) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { OfflineMapData(StopCatalog.load(context), PhilippineMaps.load(context), PilotMaps.load(context)) }
        }
    }
}

@Composable
internal fun OfflineMapPreparation(loading: Boolean, retry: () -> Unit) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (loading) "Opening offline maps…" else "Couldn’t prepare offline maps. Check available storage and try again.")
        if (!loading) TextButton(onClick = retry) { Text("Retry offline maps") }
    }
}
