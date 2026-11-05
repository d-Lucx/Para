package com.dlucx.para

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

internal data class MappedEntrance(
    val id: String, val landmarkId: String, val point: CorridorPoint,
    val kind: String, val access: String,
) {
    val selectable: Boolean get() = access !in listOf("no", "private") && kind != "exit"
}

internal class PilotMaps private constructor(val url: String, private val entrances: List<MappedEntrance>) {
    fun entrancesFor(id: String?): List<MappedEntrance> = entrances.filter { it.landmarkId == id }.distinctBy { it.id }

    companion object {
        @Volatile private var cached: PilotMaps? = null
        fun load(context: Context): PilotMaps = cached ?: synchronized(this) {
            cached ?: run {
                val index = context.assets.open("pilot/index.json").bufferedReader().use { JSONObject(it.readText()) }
                val hash = index.getString("sha256")
                val file = context.offlineAssetFile("pilot/detail.pmtiles", "pilot-${hash.take(12)}.pmtiles", index.getLong("bytes"), hash)
                val data = context.assets.open("pilot/entrances.json").bufferedReader().use { JSONArray(it.readText()) }
                val entries = List(data.length()) { i ->
                    val item = data.getJSONObject(i)
                    MappedEntrance(item.getString("id"), item.getString("landmark_id"),
                        CorridorPoint(item.getDouble("latitude"), item.getDouble("longitude")),
                        item.getString("kind"), item.getString("access"))
                }
                PilotMaps("pmtiles://" + Uri.fromFile(file), entries).also { result ->
                    context.noBackupFilesDir.listFiles()?.filter {
                        it.name.startsWith("pilot-") && it.name.endsWith(".pmtiles") && it.name != file.name
                    }?.forEach { it.delete() }
                    cached = result
                }
            }
        }
    }
}
