package com.dlucx.para

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class OfflineMapData(val catalog: StopCatalog, val maps: PhilippineMaps, val pilot: PilotMaps)

@Composable
internal fun rememberOfflineMapData(): State<Result<OfflineMapData>?> {
    val context = LocalContext.current.applicationContext
    return produceState<Result<OfflineMapData>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { OfflineMapData(StopCatalog.load(context), PhilippineMaps.load(context), PilotMaps.load(context)) }
        }
    }
}
