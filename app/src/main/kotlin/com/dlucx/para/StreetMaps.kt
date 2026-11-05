package com.dlucx.para

import android.content.Context
import android.net.Uri
import org.json.JSONArray

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
            val file = context.offlineAssetFile(
                "streets/${item.getString("file")}", "streets-$id-${hash.take(12)}.pmtiles",
                item.getLong("bytes"), hash,
            )
            StreetPack(id, "pmtiles://" + Uri.fromFile(file))
        }
        val currentNames = packs.map { Uri.parse(it.url.removePrefix("pmtiles://")).lastPathSegment }.toSet()
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("streets-") && it.name.endsWith(".pmtiles") && it.name !in currentNames
        }?.forEach { it.delete() }
        return packs
    }
}
