package com.dlucx.para

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class HomePosition(val point: CorridorPoint, val accuracyMeters: Int, val heading: Int?)

private const val FixMaxAgeMillis = 15_000L

@SuppressLint("MissingPermission")
@Composable
internal fun rememberHomePosition(enabled: Boolean): HomePosition? {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = (LocalActivity.current as LifecycleOwner).lifecycle
    var fix by remember { mutableStateOf<Location?>(null) }
    var magneticHeading by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(fix) {
        val current = fix ?: return@LaunchedEffect
        val age = SystemClock.elapsedRealtime() - current.elapsedRealtimeNanos / 1_000_000
        delay((FixMaxAgeMillis - age).coerceAtLeast(0))
        fix = null
    }
    DisposableEffect(enabled, lifecycle, view) {
        val locations = context.getSystemService(LocationManager::class.java)
        val sensors = context.getSystemService(SensorManager::class.java)
        val rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val age = SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000
                if (location.hasAccuracy() && location.accuracy in 1f..50f && age in 0 until FixMaxAgeMillis) {
                    fix = location
                }
            }
            override fun onProviderDisabled(provider: String) { fix = null }
        }
        val compass = object : SensorEventListener {
            private val matrix = FloatArray(9)
            private val screenMatrix = FloatArray(9)
            private val angles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                val accuracy = event.values.getOrNull(4)
                if (event.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ||
                    accuracy == null || accuracy !in 0f..0.52f
                ) {
                    magneticHeading = null
                    return
                }
                val (x, y) = when (view.display?.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.remapCoordinateSystem(matrix, x, y, screenMatrix)
                SensorManager.getOrientation(screenMatrix, angles)
                magneticHeading = if (abs(angles[1]) < 1.3f) Math.toDegrees(angles[0].toDouble()).roundToInt() else null
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) magneticHeading = null
            }
        }
        fun stop() {
            locations.removeUpdates(listener)
            sensors.unregisterListener(compass)
            fix = null
            magneticHeading = null
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (!enabled || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED
                ) return
                try {
                    locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
                    rotation?.let { sensors.registerListener(compass, it, 200_000) }
                } catch (_: SecurityException) {
                    stop()
                } catch (_: IllegalArgumentException) {
                    stop()
                }
            }
            override fun onPause(owner: LifecycleOwner) = stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    val current = fix ?: return null
    val declination = remember(current) {
        GeomagneticField(current.latitude.toFloat(), current.longitude.toFloat(), current.altitude.toFloat(), current.time).declination
    }
    val heading = magneticHeading?.let { Math.floorMod((it + declination).roundToInt(), 360) }
        ?: current.bearing.takeIf { current.hasBearingAccuracy() && current.bearingAccuracyDegrees in 0f..30f &&
            current.hasSpeed() && current.speed >= 2f }?.roundToInt()?.let { it % 360 }
    return HomePosition(CorridorPoint(current.latitude, current.longitude), current.accuracy.roundToInt(), heading)
}
