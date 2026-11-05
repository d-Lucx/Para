package com.dlucx.para

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.location.Location
import android.net.Uri
import android.util.AtomicFile
import android.view.MotionEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class CorridorPoint(val latitude: Double, val longitude: Double) {
    fun isInsideCorridorCoverage(): Boolean = latitude in South..North && longitude in West..East
    fun asPoint(): Point = Point.fromLngLat(longitude, latitude)
}

private const val South = 6.99
private const val North = 7.48
private const val West = 125.51
private const val East = 125.89
private const val Overlay = "para-overlay"
private val bounds = LatLngBounds.from(North, East, South, West)

@Composable
internal fun CorridorMap(
    selectedPoint: CorridorPoint?,
    approachRadiusMeters: Int,
    arrivalRadiusMeters: Int,
    onPointSelected: (CorridorPoint) -> Unit,
    onOutsideCoverage: () -> Unit,
    modifier: Modifier = Modifier,
    onApproachRadiusChanged: (Int) -> Unit = {},
    onArrivalRadiusChanged: (Int) -> Unit = {},
    recordedSegments: List<List<TripPoint>> = emptyList(),
    readOnly: Boolean = false,
) {
    val context = LocalContext.current
    val archive by produceState<Result<String>?>(null, context.applicationContext) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.noBackupFilesDir, "tagum-davao-649790e3.pmtiles")
                val asset = "tagum-davao.pmtiles"
                val expectedLength = context.assets.openFd(asset).use { it.length }
                if (file.length() != expectedLength) {
                    val atomic = AtomicFile(file)
                    val output = atomic.startWrite()
                    try {
                        context.assets.open(asset).use { input ->
                            check(input.copyTo(output) == expectedLength)
                        }
                        atomic.finishWrite(output)
                    } catch (failure: Exception) {
                        atomic.failWrite(output)
                        throw failure
                    }
                }
                "pmtiles://" + Uri.fromFile(file)
            }
        }
    }
    val archiveUrl = archive?.getOrNull()
    if (archiveUrl == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(if (archive == null) "Opening offline map…" else "Offline map unavailable")
        }
        return
    }
    val lifecycle = (LocalActivity.current as LifecycleOwner).lifecycle
    val colors = MaterialTheme.colorScheme
    val latestPoint by rememberUpdatedState(selectedPoint)
    val latestApproach by rememberUpdatedState(approachRadiusMeters)
    val latestArrival by rememberUpdatedState(arrivalRadiusMeters)
    val select by rememberUpdatedState(onPointSelected)
    val outside by rememberUpdatedState(onOutsideCoverage)
    val changeApproach by rememberUpdatedState(onApproachRadiusChanged)
    val changeArrival by rememberUpdatedState(onArrivalRadiusChanged)
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val view = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapLibre.setConnected(false)
        MapView(context, MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .logoEnabled(false)
            .attributionEnabled(false))
            .apply {
                contentDescription = if (readOnly) "Offline map of recorded trip"
                else "Offline Tagum–Davao map. Tap inside coverage to place a pin."
                onCreate(null)
            }
    }
    DisposableEffect(view, lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = view.onStart()
            override fun onResume(owner: LifecycleOwner) = view.onResume()
            override fun onPause(owner: LifecycleOwner) = view.onPause()
            override fun onStop(owner: LifecycleOwner) = view.onStop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) view.onPause()
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) view.onStop()
            lifecycle.removeObserver(observer)
            view.onDestroy()
        }
    }

    val background = colors.surface.hex()
    val textColor = colors.onSurface.toArgb()
    val primary = colors.primary.hex()
    val outline = colors.outline.hex()
    val minor = colors.outlineVariant.hex()
    val water = colors.secondary.hex()
    val arrival = colors.error.hex()
    val approach = if (colors.surface.luminance() < 0.3f) "#FFB84C" else "#875000"
    val mapStyle = remember(archiveUrl, background, primary, outline, minor, water, approach, arrival) {
        styleJson(archiveUrl, background, primary, outline, minor, water, approach, arrival)
    }
    DisposableEffect(view, mapStyle, readOnly) {
        var active = true
        var listener: Pair<MapLibreMap, MapLibreMap.OnMapClickListener>? = null
        val failure = MapView.OnDidFailLoadingMapListener { if (active) failed = true }
        view.addOnDidFailLoadingMapListener(failure)
        view.getMapAsync { nativeMap ->
            if (!active) return@getMapAsync
            nativeMap.setMinZoomPreference(8.0)
            nativeMap.setMaxZoomPreference(17.0)
            nativeMap.uiSettings.isRotateGesturesEnabled = false
            nativeMap.uiSettings.isTiltGesturesEnabled = false
            val click = MapLibreMap.OnMapClickListener { location ->
                if (!readOnly) {
                    val point = CorridorPoint(location.latitude, location.longitude)
                    if (point.isInsideCorridorCoverage()) select(point) else outside()
                }
                true
            }
            nativeMap.addOnMapClickListener(click)
            listener = nativeMap to click
            var dragging: String? = null
            view.setOnTouchListener { _, event ->
                val point = latestPoint
                if (readOnly || point == null) return@setOnTouchListener false
                val projection = nativeMap.projection
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val target = PointF(event.x, event.y)
                    val center = projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                    val hit = 18 * context.resources.displayMetrics.density
                    dragging = listOf("approach" to latestApproach, "arrival" to -latestArrival)
                        .firstOrNull { (_, radius) ->
                            if (radius == 0) return@firstOrNull false
                            val handle = ringPoint(point, kotlin.math.abs(radius), if (radius > 0) 0.0 else PI)
                            val screen = projection.toScreenLocation(LatLng(handle.latitude(), handle.longitude()))
                            kotlin.math.hypot(screen.x - center.x, screen.y - center.y) > hit &&
                                kotlin.math.hypot(screen.x - target.x, screen.y - target.y) <= hit
                        }?.first
                    return@setOnTouchListener dragging != null
                }
                val handle = dragging ?: return@setOnTouchListener false
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val position = projection.fromScreenLocation(PointF(event.x, event.y))
                    val distance = FloatArray(1)
                    Location.distanceBetween(point.latitude, point.longitude, position.latitude, position.longitude, distance)
                    if (handle == "approach") changeApproach(distance[0].roundToInt())
                    else changeArrival(distance[0].roundToInt())
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    dragging = null
                }
                true
            }
            nativeMap.setStyle(Style.Builder().fromJson(mapStyle)) { loaded ->
                if (!active) return@setStyle
                val labels = context.assets.open("tagum-davao-place-labels.json")
                    .bufferedReader().use { JSONArray(it.readText()) }
                for (index in 0 until labels.length()) {
                    val label = labels.getString(index)
                    loaded.addImage(label, labelBitmap(label, textColor, colors.surface.toArgb()))
                }
                style = loaded
                failed = false
                if (map == null) view.post {
                    if (active && view.width > 0 && view.height > 0) {
                        nativeMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 24))
                    }
                }
                map = nativeMap
            }
        }
        onDispose {
            active = false
            listener?.let { (nativeMap, click) -> nativeMap.removeOnMapClickListener(click) }
            view.setOnTouchListener(null)
            view.removeOnDidFailLoadingMapListener(failure)
        }
    }
    LaunchedEffect(style, selectedPoint, approachRadiusMeters, arrivalRadiusMeters, recordedSegments) {
        style?.getSourceAs<GeoJsonSource>(Overlay)
            ?.setGeoJson(overlay(selectedPoint, approachRadiusMeters, arrivalRadiusMeters, recordedSegments))
    }
    LaunchedEffect(map, selectedPoint) {
        val point = selectedPoint ?: return@LaunchedEffect
        val nativeMap = map ?: return@LaunchedEffect
        val location = LatLng(point.latitude, point.longitude)
        if (!nativeMap.projection.visibleRegion.latLngBounds.contains(location)) {
            nativeMap.moveCamera(CameraUpdateFactory.newLatLng(location))
        }
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if (failed) Text("Offline map unavailable", Modifier.align(Alignment.Center))
        Surface(Modifier.align(Alignment.BottomEnd)) {
            Text(
                "© OpenStreetMap contributors · ODbL\nopenstreetmap.org/copyright",
                Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun labelBitmap(label: String, color: Int, background: Int): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f }
    val image = Bitmap.createBitmap(paint.measureText(label).toInt() + 14, 34, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawColor(background)
    paint.color = color
    canvas.drawText(label, 7f, 25f, paint)
    return image
}

private fun overlay(
    selected: CorridorPoint?,
    approach: Int,
    arrival: Int,
    segments: List<List<TripPoint>>,
): FeatureCollection {
    val features = mutableListOf<Feature>()
    fun line(kind: String, points: List<Point>) {
        features += Feature.fromGeometry(LineString.fromLngLats(points))
            .apply { addStringProperty("kind", kind) }
    }
    fun marker(kind: String, point: Point) {
        features += Feature.fromGeometry(point).apply { addStringProperty("kind", kind) }
    }
    line("boundary", listOf(
        Point.fromLngLat(West, South), Point.fromLngLat(East, South),
        Point.fromLngLat(East, North), Point.fromLngLat(West, North), Point.fromLngLat(West, South),
    ))
    for (segment in segments) {
        val points = segment.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (points.size >= 2) line("track", points)
        else points.firstOrNull()?.let { marker("track-point", it) }
    }
    if (selected != null) {
        marker("pin", selected.asPoint())
        for ((kind, radius) in listOf("approach" to approach, "arrival" to arrival)) {
            if (radius > 0) {
                line(kind, (0..64).map { ringPoint(selected, radius, it * PI / 32) })
                marker(kind + "-handle", ringPoint(selected, radius, if (kind == "approach") 0.0 else PI))
            }
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun ringPoint(center: CorridorPoint, radius: Int, angle: Double): Point {
    val latitude = center.latitude + radius * sin(angle) / 111_195.0
    val longitude = center.longitude + radius * cos(angle) /
        (111_195.0 * cos(Math.toRadians(center.latitude)))
    return Point.fromLngLat(longitude, latitude)
}

private fun Color.hex(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

private fun styleJson(
    archiveUrl: String, background: String, primary: String, outline: String, minor: String,
    water: String, approach: String, arrival: String,
): String {
    fun line(id: String, source: String, kind: String, color: String, width: Double, dash: Boolean = false): String {
        val sourceLayer = if (source == "corridor") ""","source-layer":"lines"""" else ""
        val dashPaint = if (dash) ""","line-dasharray":[2,2]""" else ""
        return """{"id":"$id","type":"line","source":"$source"$sourceLayer,
            "filter":["==","kind","$kind"],"paint":{"line-color":"$color","line-width":$width$dashPaint}}"""
    }
    fun circle(kind: String, color: String, radius: Int) =
        """{"id":"$kind","type":"circle","source":"$Overlay","filter":["==","kind","$kind"],
            "paint":{"circle-color":"$color","circle-radius":$radius,"circle-stroke-color":"$background","circle-stroke-width":1}}"""
    val layers = listOf(
        """{"id":"background","type":"background","paint":{"background-color":"$background"}}""",
        line("local-roads", "corridor", "local", minor, 1.0),
        line("rivers", "corridor", "water", water, 1.2),
        line("coast", "corridor", "coast", water, 2.0),
        line("main-roads", "corridor", "main", outline, 1.75),
        line("coverage", Overlay, "boundary", outline, 2.0, dash = true),
        line("track", Overlay, "track", primary, 3.5),
        line("approach", Overlay, "approach", approach, 2.0, dash = true),
        line("arrival", Overlay, "arrival", arrival, 2.0),
        circle("track-point", primary, 3),
        circle("pin", primary, 6),
        circle("approach-handle", approach, 4),
        circle("arrival-handle", arrival, 4),
        """{"id":"places","type":"symbol","source":"corridor","source-layer":"places",
            "layout":{"icon-image":["get","name"],"icon-size":0.65,"icon-anchor":"bottom-left"}}""",
    )
    return """{"version":8,"sources":{
        "corridor":{"type":"vector","url":"$archiveUrl"},
        "$Overlay":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}
        },"layers":[""" + layers.joinToString(",") + "]}"
}

