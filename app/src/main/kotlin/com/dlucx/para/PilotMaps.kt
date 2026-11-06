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

internal data class DetailMapPack(val id: String, val url: String, val boundsJson: String)

internal class PilotMaps private constructor(val packs: List<DetailMapPack>, private val entrances: List<MappedEntrance>) {
    fun entrancesFor(id: String?): List<MappedEntrance> = entrances.filter { it.landmarkId == id }.distinctBy { it.id }

    companion object {
        @Volatile private var cached: PilotMaps? = null
        fun load(context: Context): PilotMaps = cached ?: synchronized(this) {
            cached ?: run {
                val directories = listOf("pilot" to "pilot") +
                    context.assets.list("detail").orEmpty().sorted().map { it to "detail/$it" }
                val files = directories.map { (id, directory) ->
                    val index = context.assets.open("$directory/index.json").bufferedReader().use { JSONObject(it.readText()) }
                    val hash = index.getString("sha256")
                    val file = context.offlineAssetFile("$directory/detail.pmtiles", "$id-${hash.take(12)}.pmtiles",
                        index.getLong("bytes"), hash)
                    val bounds = index.getJSONArray("bounds")
                    val west = bounds.getDouble(0)
                    val south = bounds.getDouble(1)
                    val east = bounds.getDouble(2)
                    val north = bounds.getDouble(3)
                    val polygon = """{"type":"Polygon","coordinates":[[[$west,$south],[$east,$south],[$east,$north],[$west,$north],[$west,$south]]]}"""
                    DetailMapPack(id, "pmtiles://" + Uri.fromFile(file), polygon) to file.name
                }
                val entries = directories.flatMap { (_, directory) ->
                    val data = context.assets.open("$directory/entrances.json").bufferedReader().use { JSONArray(it.readText()) }
                    List(data.length()) { i ->
                        val item = data.getJSONObject(i)
                        MappedEntrance(item.getString("id"), item.getString("landmark_id"),
                            CorridorPoint(item.getDouble("latitude"), item.getDouble("longitude")),
                            item.getString("kind"), item.getString("access"))
                    }
                }
                PilotMaps(files.map { it.first }, entries).also { result ->
                    context.noBackupFilesDir.listFiles()?.filter { file ->
                        files.any { (pack, name) ->
                            file.name != name && file.name.matches(Regex("${Regex.escape(pack.id)}-[0-9a-f]{12}\\.pmtiles"))
                        }
                    }?.forEach { it.delete() }
                    cached = result
                }
            }
        }
    }
}
