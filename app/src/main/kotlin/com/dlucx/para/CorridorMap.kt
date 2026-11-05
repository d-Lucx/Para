package com.dlucx.para

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class CorridorPoint(val latitude: Double, val longitude: Double) {
    fun isInsidePilotCoverage(): Boolean = latitude in South..North && longitude in West..East
    fun asPoint(): Point = Point.fromLngLat(longitude, latitude)
}

private const val South = 6.99
private const val North = 7.48
private const val West = 125.51
private const val East = 125.89


@Composable
internal fun CorridorMap(
    selectedPoint: CorridorPoint?,
    approachRadiusMeters: Int,
    arrivalRadiusMeters: Int,
    onPointSelected: (CorridorPoint, String?) -> Unit,
    onOutsideCoverage: () -> Unit,
    modifier: Modifier = Modifier,
    onApproachRadiusChanged: (Int) -> Unit = {},
    onArrivalRadiusChanged: (Int) -> Unit = {},
    recordedSegments: List<List<TripPoint>> = emptyList(),
    readOnly: Boolean = false,
    region: MapRegion? = null,
    focusSelection: Boolean = false,
    snapToLandmarks: Boolean = true,
) {
    val context = LocalContext.current
    val largeText = LocalDensity.current.fontScale > 1.3f
    var mapAttempt by remember { mutableIntStateOf(0) }
    val mapData by rememberOfflineMapData(mapAttempt)
    val loaded = mapData?.getOrNull()
    if (loaded == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            OfflineMapPreparation(mapData == null) { mapAttempt++ }
        }
        return
    }
    val regionalMaps = loaded.maps
    val places = loaded.catalog.mapPlaces
    val placeFeatures = remember(places) {
        FeatureCollection.fromFeatures(places.map { place ->
            Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude)).apply {
                addStringProperty("name", place.name)
                addBooleanProperty("pilot", place.isPilotStop)
            }
        })
    }
    val archive by produceState<Result<Pair<String, List<StreetPack>>>?>(null, context.applicationContext, mapAttempt) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val asset = "tagum-davao.pmtiles"
                val expectedLength = context.assets.openFd(asset).use { it.length }
                val file = context.offlineAssetFile(
                    asset, "tagum-davao-649790e3.pmtiles", expectedLength,
                    "649790e3f6fa5d1272192d99c90cb81999b7eafb7cab7d61314bd76fdca7c658",
                )
                ("pmtiles://" + Uri.fromFile(file)) to StreetMaps.load(context.applicationContext)
            }
        }
    }
    val archives = archive?.getOrNull()
    if (archives == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            OfflineMapPreparation(archive == null) { mapAttempt++ }
        }
        return
    }
    val (archiveUrl, streetPacks) = archives
    val lifecycle = (LocalActivity.current as LifecycleOwner).lifecycle
    val colors = MaterialTheme.colorScheme
    val latestPoint by rememberUpdatedState(selectedPoint)
    val latestApproach by rememberUpdatedState(approachRadiusMeters)
    val latestArrival by rememberUpdatedState(arrivalRadiusMeters)
    val select by rememberUpdatedState(onPointSelected)
    val snap by rememberUpdatedState(snapToLandmarks)
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
                else "Offline Philippine map. Tap a named place or place a custom pin."
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
    val water = if (colors.surface.luminance() < 0.3f) "#6594AD" else "#99C8DE"
    val land = colors.surfaceContainer.hex()
    val landAlternate = colors.surfaceContainerHigh.hex()
    val arrival = colors.error.hex()
    val approach = if (colors.surface.luminance() < 0.3f) "#FFB84C" else "#875000"
    val mapStyle = remember(archiveUrl, background, primary, outline, minor, water, approach, arrival, land, landAlternate) {
        styleJson(archiveUrl, streetPacks, background, primary, outline, minor, water, approach, arrival, land, landAlternate,
            loaded.pilot.url, colors.surface.luminance() < 0.3f)
    }
    DisposableEffect(view, mapStyle, readOnly) {
        var active = true
        var listener: Pair<MapLibreMap, MapLibreMap.OnMapClickListener>? = null
        val failure = MapView.OnDidFailLoadingMapListener { if (active) failed = true }
        view.addOnDidFailLoadingMapListener(failure)
        view.getMapAsync { nativeMap ->
            if (!active) return@getMapAsync
            nativeMap.setMinZoomPreference(4.0)
            nativeMap.setMaxZoomPreference(17.0)
            nativeMap.uiSettings.isRotateGesturesEnabled = false
            nativeMap.uiSettings.isTiltGesturesEnabled = false
            val click = MapLibreMap.OnMapClickListener { location ->
                if (!readOnly) {
                    val point = CorridorPoint(location.latitude, location.longitude)
                    val pixel = nativeMap.projection.toScreenLocation(location)
                    val hit = 10f * context.resources.displayMetrics.density
                    val namedLayers = listOf("national-labels", "pilot-stop-labels", "national-points", "pilot-stop-points", "places") +
                        streetPacks.flatMap { listOf("landmarks-${it.id}", "landmark-points-${it.id}",
                            "landmarks-detail-${it.id}", "landmark-points-detail-${it.id}") }
                    val named = if (snap) nativeMap.queryRenderedFeatures(
                        RectF(pixel.x - hit, pixel.y - hit, pixel.x + hit, pixel.y + hit),
                        *namedLayers.toTypedArray(),
                    ).firstOrNull() else null
                    val anchor = named?.geometry() as? Point
                    val chosen = if (anchor == null) point else CorridorPoint(anchor.latitude(), anchor.longitude())
                    if (regionalMaps.covers(chosen)) select(chosen, named?.getStringProperty("name")) else outside()
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
                loaded.getSourceAs<GeoJsonSource>("regions")?.setGeoJson(regionalMaps.boundaries)
                val regionNames = regionalMaps.regions.map { item ->
                    loaded.addImage("region:${item.shortName}", labelBitmap(item.shortName, textColor, colors.surface.toArgb()))
                    val center = item.bounds.center
                    Feature.fromGeometry(Point.fromLngLat(center.longitude, center.latitude)).apply {
                        addStringProperty("name", item.shortName)
                    }
                }
                loaded.getSourceAs<GeoJsonSource>("region-names")?.setGeoJson(FeatureCollection.fromFeatures(regionNames))
                loaded.getSourceAs<GeoJsonSource>("named-places")?.setGeoJson(placeFeatures)
                style = loaded
                failed = false
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
    LaunchedEffect(map, region) {
        val nativeMap = map ?: return@LaunchedEffect
        view.post {
            if (view.isAttachedToWindow && view.width > 0 && view.height > 0) {
                nativeMap.moveCamera(CameraUpdateFactory.newLatLngBounds(region?.bounds ?: regionalMaps.bounds, 24))
            }
        }
    }
    LaunchedEffect(map, selectedPoint, focusSelection) {
        if (readOnly && recordedSegments.any { it.isNotEmpty() }) return@LaunchedEffect
        val point = selectedPoint ?: return@LaunchedEffect
        val nativeMap = map ?: return@LaunchedEffect
        val location = LatLng(point.latitude, point.longitude)
        view.post {
            if (!view.isAttachedToWindow) return@post
            if (focusSelection) {
                val detailZoom = if (point.isInsidePilotCoverage()) 15.5 else 13.5
                nativeMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, maxOf(detailZoom, nativeMap.cameraPosition.zoom)))
            } else if (!nativeMap.projection.visibleRegion.latLngBounds.contains(location)) {
                nativeMap.moveCamera(CameraUpdateFactory.newLatLng(location))
            }
        }
    }
    LaunchedEffect(map, recordedSegments, readOnly) {
        if (!readOnly) return@LaunchedEffect
        val nativeMap = map ?: return@LaunchedEffect
        val points = recordedSegments.flatten()
        if (points.isEmpty()) return@LaunchedEffect
        view.post {
            if (!view.isAttachedToWindow || view.width == 0 || view.height == 0) return@post
            val south = points.minOf { it.latitude }
            val north = points.maxOf { it.latitude }
            val west = points.minOf { it.longitude }
            val east = points.maxOf { it.longitude }
            if (south == north && west == east) nativeMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(south, west), 15.0))
            else nativeMap.moveCamera(CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.from(north, east, south, west), 40))
        }
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) },
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)) { Text("+", Modifier.semantics { contentDescription = "Zoom in" }) }
            OutlinedButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) },
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)) { Text("−", Modifier.semantics { contentDescription = "Zoom out" }) }
        }
        if (!readOnly && selectedPoint != null && approachRadiusMeters > 0 && arrivalRadiusMeters > 0) {
            Surface(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Text("Approach $approachRadiusMeters m\nArrival $arrivalRadiusMeters m", Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (failed) Text("Offline map unavailable", Modifier.align(Alignment.Center))
        Surface(Modifier.align(Alignment.BottomEnd)) {
            Text(
                if (largeText) "© OpenStreetMap contributors · GeoNames"
                else "© OpenStreetMap contributors · GeoNames\nRegional boundaries: NAMRIA / PSA",
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
    segments.firstOrNull { it.isNotEmpty() }?.firstOrNull()?.let {
        marker("track-start", Point.fromLngLat(it.longitude, it.latitude))
    }
    segments.lastOrNull { it.isNotEmpty() }?.lastOrNull()?.let {
        marker("track-end", Point.fromLngLat(it.longitude, it.latitude))
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

