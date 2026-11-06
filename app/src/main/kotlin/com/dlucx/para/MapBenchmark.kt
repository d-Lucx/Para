package com.dlucx.para

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

internal fun Context.isMapBenchmark(): Boolean =
    packageName.endsWith(".mapbench") && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

@SuppressLint("LogNotTimber")
internal fun mapBenchmarkLog(message: String) {
    Log.i("ParaMapPerf", message)
}

/** Render callback cadence for the isolated benchmark package. */
internal class MapBenchmark(private val view: MapView, private val map: MapLibreMap) {
    private val started = SystemClock.elapsedRealtimeNanos()
    private var styleReady = false
    private var firstFull = false
    private var cameraReason: Int? = null
    private var lastFrame = 0L
    private val gaps = LongArray(512)
    private var gapCount = 0

    private val frame = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
        val now = SystemClock.elapsedRealtimeNanos()
        if (styleReady && fully && !firstFull) {
            firstFull = true
            mapBenchmarkLog("renderer_full us=${(now - started) / 1_000}")
        }
        if (cameraReason != null) {
            if (lastFrame != 0L && gapCount < gaps.size) gaps[gapCount++] = (now - lastFrame) / 1_000
            lastFrame = now
        }
    }
    private val move = MapLibreMap.OnCameraMoveStartedListener { reason ->
        cameraReason = reason
        lastFrame = 0L
        gapCount = 0
    }
    private val idle = MapLibreMap.OnCameraIdleListener {
        val reason = cameraReason
        cameraReason = null
        if (reason != null && gapCount > 0) {
            val sorted = gaps.copyOf(gapCount).apply { sort() }
            val p95 = (gapCount * 95 + 99) / 100 - 1
            mapBenchmarkLog("camera reason=$reason gaps=$gapCount p50_us=${sorted[(gapCount - 1) / 2]} p95_us=${sorted[p95]}")
        }
    }

    init {
        view.addOnDidFinishRenderingFrameListener(frame)
        map.addOnCameraMoveStartedListener(move)
        map.addOnCameraIdleListener(idle)
    }

    fun stylePopulated() {
        styleReady = true
    }

    fun close() {
        view.removeOnDidFinishRenderingFrameListener(frame)
        map.removeOnCameraMoveStartedListener(move)
        map.removeOnCameraIdleListener(idle)
    }
}
