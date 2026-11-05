package com.dlucx.para

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class ArrivalActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (TripService.state.value?.alert != TripAlert.Arrival) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        enableEdgeToEdge()
        val preferences = getSharedPreferences("para_settings", MODE_PRIVATE)
        val showDetails = preferences.getBoolean(ShowLockScreenDetails, false)
        setContent {
            val trip by TripService.state.collectAsState()
            val arrival = trip?.takeIf { it.alert == TripAlert.Arrival }
            if (arrival == null) {
                LaunchedEffect(Unit) { finish() }
            } else {
                ParaTheme(preferences.readAppearance()) {
                    ActiveTrip(
                        snapshot = arrival,
                        onEnd = { TripService.end(this) },
                        onKeepTracking = {},
                        showDestinationDetails = showDetails,
                    )
                }
            }
        }
    }
}
