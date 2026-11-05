package com.dlucx.para

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToLong

internal data class TransitStop(val id: String, val name: String, val locality: String, val latitude: Double, val longitude: Double) {
    val isPilotStop: Boolean get() = !id.startsWith("geo:") && !id.startsWith("osm:")
}

/** Immutable public map data; private destinations and trips remain encrypted separately. */
internal class StopCatalog private constructor(private val database: SQLiteDatabase) {
    fun search(query: String): List<TransitStop> {
        val terms = searchTerms(query)
        if (terms.isEmpty()) return emptyList()
        // Implicit AND works with Android FTS4 builds that omit the enhanced query parser.
        val match = terms.joinToString(" ") { "$it*" }
        val name = terms.joinToString(" ")
        return database.rawQuery("""
            SELECT p.id,p.name,p.locality,p.latitude,p.longitude FROM places p
            JOIN search s ON p.rowid=s.rowid WHERE search MATCH ?
            ORDER BY CASE WHEN p.name_key=? THEN 0
                WHEN p.id NOT LIKE 'geo:%' AND p.id NOT LIKE 'osm:%' THEN 1
                WHEN p.name_key LIKE ? THEN 2 ELSE 3 END,
                p.name COLLATE NOCASE LIMIT 6
        """.trimIndent(), arrayOf(match, name, "$name%")).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.stop()) }
        }
    }

    val mapPlaces: List<TransitStop> = database.rawQuery(
        "SELECT id,name,locality,latitude,longitude FROM places WHERE show_on_map=1", null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.stop()) } }

    fun nearby(point: CorridorPoint): TransitStop? {
        val scale = cos(Math.toRadians(point.latitude)).let { it * it }
        return database.rawQuery("""
            SELECT id,name,locality,latitude,longitude,
                (latitude-?)*(latitude-?)+(longitude-?)*(longitude-?)*? AS squared
            FROM places WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
            ORDER BY squared LIMIT 1
        """.trimIndent(), arrayOf(point.latitude, point.latitude, point.longitude, point.longitude, scale,
            point.latitude - .1, point.latitude + .1, point.longitude - .1, point.longitude + .1,
        ).map { it.toString() }.toTypedArray()).use { cursor ->
            if (cursor.moveToFirst() && cursor.getDouble(5) <= .07 * .07) cursor.stop() else null
        }
    }

    fun regionNameAt(point: CorridorPoint): String? {
        val lat = (point.latitude * 1e7).roundToLong()
        val lon = (point.longitude * 1e7).roundToLong()
        // MVT quantization can shift rendered anchors by about a metre.
        return database.rawQuery(
            "SELECT region FROM places WHERE lat_e7 BETWEEN ? AND ? AND lon_e7 BETWEEN ? AND ? AND region!='' LIMIT 1",
            arrayOf(lat - 200, lat + 200, lon - 200, lon + 200).map { it.toString() }.toTypedArray(),
        ).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun Cursor.stop() = TransitStop(getString(0), getString(1), getString(2), getDouble(3), getDouble(4))

    companion object {
        private val marks = Regex("\\p{M}+")
        private val separators = Regex("[^\\p{L}\\p{N}]+")
        @Volatile private var cached: StopCatalog? = null

        fun load(context: Context): StopCatalog = cached ?: synchronized(this) {
            cached ?: read(context.applicationContext).also { cached = it }
        }

        private fun read(context: Context): StopCatalog {
            val metadata = context.assets.open("place-search.json").bufferedReader().use { JSONObject(it.readText()) }
            val hash = metadata.getString("sha256")
            val file = context.offlineAssetFile("place-search.sqlite", "place-search-${hash.take(12)}.sqlite",
                metadata.getLong("bytes"), hash)
            val catalog = StopCatalog(SQLiteDatabase.openDatabase(file.path, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS))
            context.noBackupFilesDir.listFiles()?.filter {
                it.name.startsWith("place-search-") && it.name.endsWith(".sqlite") && it.name != file.name
            }?.forEach { it.delete() }
            return catalog
        }

        private fun searchTerms(value: String): List<String> = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(marks, "").split(separators).filter { it.isNotEmpty() }
    }
}
