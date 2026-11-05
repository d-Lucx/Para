package com.dlucx.para

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

internal data class StreetPack(val id: String, val url: String)

/** Versioned local files avoid MapLibre's compressed asset PMTiles limitation. */
internal object StreetMaps {
    @Volatile private var cached: List<StreetPack>? = null

    fun load(context: Context): List<StreetPack> = cached ?: synchronized(this) {
        cached ?: read(context).also { cached = it }
    }

    private fun read(context: Context): List<StreetPack> {
        val index = context.assets.open("streets/index.json").bufferedReader().use { JSONArray(it.readText()) }
        val packs = List(index.length()) { position ->
            val item = index.getJSONObject(position)
            val hash = item.getString("sha256")
            val id = item.getString("id")
            val file = File(context.noBackupFilesDir, "streets-$id-${hash.take(12)}.pmtiles")
            val expected = item.getLong("bytes")
            if (file.length() != expected) {
                val atomic = AtomicFile(file)
                val output = atomic.startWrite()
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    context.assets.open("streets/${item.getString("file")}").use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            total += count
                        }
                        check(total == expected)
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == hash)
                    atomic.finishWrite(output)
                } catch (failure: Exception) {
                    atomic.failWrite(output)
                    throw failure
                }
            }
            StreetPack(id, "pmtiles://" + Uri.fromFile(file))
        }
        val currentNames = packs.map { Uri.parse(it.url.removePrefix("pmtiles://")).lastPathSegment }.toSet()
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("streets-") && it.name.endsWith(".pmtiles") && it.name !in currentNames
        }?.forEach { it.delete() }
        return packs
    }
}
