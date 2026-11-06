package com.dlucx.para

import android.content.Context
import org.json.JSONArray
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal class MapRegion(
    val id: String,
    val name: String,
    val feature: Feature,
    private val polygons: List<List<List<Point>>>,
) {
    val shortName: String = when {
        name.contains("(NCR)") -> "NCR"
        name.contains("(CAR)") -> "CAR"
        name.contains("(BARMM)") -> "BARMM"
        name.contains("(NIR)") -> "Negros Island"
        else -> name.substringBefore(" (").removeSuffix(" Region")
    }
    val bounds: LatLngBounds = polygons.flatten().flatten().let { points ->
        LatLngBounds.from(
            points.maxOf { it.latitude() }, points.maxOf { it.longitude() },
            points.minOf { it.latitude() }, points.minOf { it.longitude() },
        )
    }

    fun contains(point: CorridorPoint): Boolean =
        point.latitude in bounds.latitudeSouth..bounds.latitudeNorth &&
            point.longitude in bounds.longitudeWest..bounds.longitudeEast &&
            polygons.any { polygon ->
                insideRing(point, polygon.first()) && polygon.drop(1).none { insideRing(point, it) }
            }

    private fun insideRing(point: CorridorPoint, ring: List<Point>): Boolean {
        var inside = false
        var previous = ring.last()
        for (next in ring) {
            if ((previous.latitude() > point.latitude) != (next.latitude() > point.latitude)) {
                val intersection = (next.longitude() - previous.longitude()) *
                    (point.latitude - previous.latitude()) / (next.latitude() - previous.latitude()) +
                    previous.longitude()
                if (point.longitude < intersection) inside = !inside
            }
            previous = next
        }
        return inside
    }
}
internal class PhilippineMaps private constructor(
    val regions: List<MapRegion>,
    private val catalog: StopCatalog,
) {
    val boundaries = FeatureCollection.fromFeatures(regions.map { it.feature })
    val bounds: LatLngBounds = LatLngBounds.from(
        regions.maxOf { it.bounds.latitudeNorth },
        regions.maxOf { it.bounds.longitudeEast },
        regions.minOf { it.bounds.latitudeSouth },
        regions.minOf { it.bounds.longitudeWest },
    )

    fun regionAt(point: CorridorPoint): MapRegion? {
        if (!point.latitude.isFinite() || !point.longitude.isFinite()) return null
        return regions.firstOrNull { it.contains(point) }
            ?: catalog.regionNameAt(point)?.let { name -> regions.firstOrNull { it.name == name } }
    }

    // Keep destinations saved against the original pilot boundary usable.
    fun covers(point: CorridorPoint): Boolean = point.isInsidePilotCoverage() || regionAt(point) != null

    companion object {
        @Volatile private var cached: PhilippineMaps? = null

        fun load(context: Context): PhilippineMaps = cached ?: synchronized(this) {
            cached ?: read(context.applicationContext).also { cached = it }
        }

        private fun read(context: Context): PhilippineMaps {
            val assets = context.assets
            val index = assets.open("regions/index.json").bufferedReader().use { JSONArray(it.readText()) }
            val regions = List(index.length()) { position ->
                val item = index.getJSONObject(position)
                val feature = assets.open("regions/${item.getString("file")}")
                    .bufferedReader().use { Feature.fromJson(it.readText()) }
                val polygons = when (val geometry = feature.geometry()) {
                    is Polygon -> listOf(geometry.coordinates())
                    is MultiPolygon -> geometry.coordinates()
                    else -> error("Invalid region geometry")
                }
                MapRegion(item.getString("id"), item.getString("name"), feature, polygons)
            }
            // Coastal/reclaimed-land landmarks can fall outside simplified land outlines.
            // Keep those exact, source-backed anchors selectable without accepting arbitrary ocean pins.
            return PhilippineMaps(regions, StopCatalog.load(context))
        }
    }
}
