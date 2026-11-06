package com.dlucx.para

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OfflineDetailSettings(
    onMap: Boolean = false,
    suggestion: DownloadableDetail? = null,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val catalog = remember(context) { context.detailCatalog() }
    fun installedIds() = catalog.filter { context.installedDetail(it) != null }.map { it.id }.toSet()
    var installed by remember { mutableStateOf(installedIds()) }
    var selected by remember { mutableStateOf<DownloadableDetail?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var activeName by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val visiblePacks = if (onMap) listOfNotNull(suggestion?.takeUnless { it.id in installed }) else catalog
    if (!onMap || visiblePacks.isNotEmpty() || job != null || message != null) ParaPanel {
        if (!onMap) Text("Detailed offline maps", style = MaterialTheme.typography.titleMedium)
        for (pack in visiblePacks) {
            if (context.isBundled(pack)) {
                Text("${pack.name} · Included")
            } else {
                val isInstalled = pack.id in installed
                Text("${pack.name} · ${formatPackSize(pack.bytes)}" + if (isInstalled) " · Installed" else "")
                TextButton(onClick = { selected = pack }, enabled = job == null) {
                    Text(if (isInstalled) "Remove ${pack.name}"
                        else if (onMap) "Download map area" else "Download ${pack.name}")
                }
            }
        }
        if (job != null) {
            Text(if (progress < 100) "Downloading $activeName · $progress%" else "Preparing $activeName…")
            TextButton(onClick = { job?.cancel() }) { Text("Cancel download") }
        }
        message?.let { Text(it) }
    }

    selected?.let { pack ->
        val remove = pack.id in installed
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(if (remove) "Remove ${pack.name}?" else "Download ${pack.name}?") },
            text = {
                Text(if (remove) "The bundled street map remains available."
                    else "${formatPackSize(pack.bytes)}. Internet is needed for the download. You can use the map offline afterward.")
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    activeName = pack.name
                    progress = if (remove) 100 else 0
                    message = null
                    job = scope.launch {
                        try {
                            if (remove) withContext(Dispatchers.IO) {
                                check(context.removeDetails(pack))
                            } else context.downloadDetail(pack) { progress = it }
                            message = if (remove) "${pack.name} removed." else "${pack.name} is available offline."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            message = if (remove) "Couldn’t remove the map area." else "Couldn’t download the map area. Check your connection and try again."
                        } finally {
                            val current = installedIds()
                            if (current != installed) {
                                PilotMaps.refreshDownloads()
                                installed = current
                                onChanged()
                            }
                            job = null
                        }
                    }
                }) { Text(if (remove) "Remove area" else "Download area") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Not now") } },
        )
    }
}

private fun formatPackSize(bytes: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
