package com.dlucx.para

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal data class DownloadableDetail(
    val id: String,
    val name: String,
    val version: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val replaces: Set<String>,
)

private val packId = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
private val hashPattern = Regex("[0-9a-f]{64}")
private const val MaxPackBytes = 256_000_000L
private val packFiles = setOf(
    "detail.pmtiles", "index.json", "entrances.json", "NOTICE.txt", "place-search.json", "place-search.sqlite.gzip",
)

internal fun Context.detailCatalog(includeSuperseded: Boolean = false): List<DownloadableDetail> {
    val catalog = assets.open("detail-catalog.json").bufferedReader().use { JSONObject(it.readText()) }
    check(catalog.getInt("schema") == 1)
    val packs = catalog.getJSONArray("packs")
    val entries = List(packs.length()) { index ->
        val entry = packs.getJSONObject(index)
        check(entry.getInt("mapSchema") == 1 && entry.getInt("searchSchema") == 1)
        val replaces = entry.optJSONArray("replaces") ?: JSONArray()
        DownloadableDetail(
            entry.getString("id"), entry.getString("name"), entry.getString("version"),
            entry.getString("url"), entry.getLong("bytes"), entry.getString("sha256"),
            List(replaces.length(), replaces::getString).toSet(),
        ).also {
            check(it.id.matches(packId) && it.version.matches(packId))
            check(it.bytes in 1..MaxPackBytes && it.sha256.matches(hashPattern))
            check(it.url == "https://github.com/d-Lucx/Para/releases/download/map-data-${it.version}/${it.id}-${it.version}.zip")
        }
    }.also { check(it.distinctBy(DownloadableDetail::id).size == it.size) }
    val superseded = entries.flatMap { it.replaces }.toSet()
    check(superseded.all { id -> entries.any { it.id == id && it.replaces.isEmpty() } })
    return if (includeSuperseded) entries else entries.filterNot { it.id in superseded }
}

internal fun Context.isBundled(detail: DownloadableDetail): Boolean = detail.id in assets.list("detail").orEmpty()

private fun Context.detailRoot(detail: DownloadableDetail) =
    File(noBackupFilesDir, "downloaded-detail/${detail.id}")

private fun File.isCompletePack(): Boolean = isDirectory && packFiles.all { File(this, it).isFile }

internal fun Context.installedDetail(detail: DownloadableDetail, allowOlder: Boolean = false): File? {
    val root = detailRoot(detail)
    val current = root.resolve(detail.version)
    if (current.isCompletePack()) return current
    if (!allowOlder) return null
    return root.listFiles()?.filter { it.name.matches(packId) && it.isCompletePack() }?.maxByOrNull(File::getName)
}

internal fun Context.removeDetails(detail: DownloadableDetail): Boolean {
    val superseded = detailCatalog(includeSuperseded = true).filter { it.id in detail.replaces }
    return superseded.all { detailRoot(it).deleteRecursively() } && detailRoot(detail).deleteRecursively()
}

internal suspend fun Context.downloadDetail(detail: DownloadableDetail, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
    if (installedDetail(detail) != null) return@withContext
    val root = detailRoot(detail)
    val target = root.resolve(detail.version)
    check(root.mkdirs() || root.isDirectory)
    val staging = java.nio.file.Files.createTempDirectory(root.toPath(), ".stage-").toFile()
    try {
        val zip = File(staging, "download.zip")
        val connection = openPackConnection(detail.url)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            zip.outputStream().buffered().use { output ->
                connection.inputStream.buffered().use { input ->
                    var last = -1
                    val total = copyBounded(input, detail.bytes) { buffer, length, count ->
                        output.write(buffer, 0, length)
                        digest.update(buffer, 0, length)
                        val percent = (count * 100 / detail.bytes).toInt()
                        if (percent != last) {
                            progress(percent)
                            last = percent
                        }
                    }
                    check(total == detail.bytes && digest.digest().toHex() == detail.sha256)
                }
            }
        } finally {
            connection.disconnect()
        }
        val entries = mutableSetOf<String>()
        var remaining = detail.bytes * 3
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                check(!entry.isDirectory && entry.name in packFiles && entries.add(entry.name))
                File(staging, entry.name).outputStream().buffered().use { output ->
                    remaining -= copyBounded(input, remaining) { buffer, length, _ -> output.write(buffer, 0, length) }
                }
                input.closeEntry()
            }
        }
        check(entries == packFiles && zip.delete())
        coroutineContext.ensureActive()
        check(staging.renameTo(target) || installedDetail(detail) != null)
    } finally {
        staging.deleteRecursively()
    }
}

private fun openPackConnection(start: String): HttpsURLConnection {
    var url = URL(start)
    repeat(4) {
        check(url.protocol == "https" && url.userInfo == null && url.port in listOf(-1, 443) &&
            url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            when (connection.responseCode) {
                200 -> return connection
                301, 302, 303, 307, 308 -> url = URL(url, checkNotNull(connection.getHeaderField("Location")))
                else -> error("Map server returned HTTP ${connection.responseCode}")
            }
        } catch (failure: Exception) {
            connection.disconnect()
            throw failure
        }
        connection.disconnect()
    }
    error("Too many map download redirects")
}

private suspend fun copyBounded(input: InputStream, limit: Long, consume: (ByteArray, Int, Long) -> Unit): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count == -1) return total
        total += count
        check(total <= limit)
        consume(buffer, count, total)
    }
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
