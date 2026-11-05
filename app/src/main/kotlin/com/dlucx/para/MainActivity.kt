package com.dlucx.para

import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var preview: MediaPlayer? = null
    private var endRequested by mutableStateOf(false)
    private var permissions by mutableStateOf(TripPermissions(false, false, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleTripIntent(intent)
        permissions = readTripPermissions()
        val preferences = getSharedPreferences("para_settings", MODE_PRIVATE)
        val destinations = SavedDestinations(this)
        setContent {
            val trip by TripService.state.collectAsState()
            ParaApp(
                preferences = preferences,
                destinations = destinations,
                onTestAlarm = ::testAlarm,
                trip = trip,
                permissions = permissions,
                notificationEndRequested = endRequested,
                onNotificationEndHandled = { endRequested = false },
                onStartTrip = { draft ->
                    stopPreview()
                    TripService.start(this, draft)
                },
                onEndTrip = { TripService.end(this) },
                onKeepTracking = { TripService.keepTracking(this) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTripIntent(intent)
    }

    private fun handleTripIntent(intent: Intent) {
        endRequested = TripService.state.value != null && intent.action == TripService.ACTION_CONFIRM_END_TRIP
        if (intent.action == TripService.ACTION_CONFIRM_END_TRIP) {
            intent.action = null
        }
    }

    override fun onResume() {
        super.onResume()
        permissions = readTripPermissions()
    }

    private fun testAlarm() {
        stopPreview()
        val started = runCatching {
            val sound = MediaPlayer.create(
                this,
                R.raw.arrival_alarm,
                TripService.AlarmAudio,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            ) ?: return@runCatching false
            preview = sound
            sound.setOnCompletionListener { if (preview === it) stopPreview() }
            sound.start()
            true
        }.getOrDefault(false)
        if (!started) {
            stopPreview()
            Toast.makeText(this, R.string.alarm_preview_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreview() {
        preview?.release()
        preview = null
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

}
