package com.dlucx.para

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionTest {
    private fun trip() = TripSession(DestinationDraft("Stop", 7.46, 125.80, 1500, 300, 1), 0)

    @Test
    fun backupFiresAtDeadlineWithoutGpsAndArrivalOverridesIt() {
        val trip = trip()
        trip.onTime(59_999)
        assertNull(trip.snapshot().alert)
        assertTrue(trip.snapshot().backupTimerActive)
        trip.onTime(60_000)
        assertEquals(TripAlert.Backup, trip.snapshot().alert)
        assertFalse(trip.snapshot().backupTimerActive)
        trip.onFix(7.46, 125.80, 10f, 60_001, 60_001)
        assertEquals(TripAlert.Arrival, trip.snapshot().alert)
    }

    @Test
    fun firstFixInArrivalZoneSkipsApproachChime() {
        val trip = trip()
        assertFalse(trip.onFix(7.46, 125.80, 10f, 1_000, 1_000))
        assertEquals(TripAlert.Arrival, trip.snapshot().alert)
    }

    @Test
    fun approachChimesOnceAndStaleFixBreaksThePath() {
        val trip = trip()
        assertTrue(trip.onFix(7.466, 125.80, 10f, 1_000, 1_000))
        assertFalse(trip.onFix(7.466, 125.80, 10f, 2_000, 2_000))
        trip.onTime(20_000)
        assertEquals(TripStatus.GpsSignalLost, trip.snapshot().status)
        assertNull(trip.snapshot().remainingMeters)
        trip.onFix(7.47, 125.80, 10f, 20_001, 20_001)
        assertEquals(2, trip.finish(20_001).segments.size)
        assertEquals(0.0, trip.finish(20_001).recordedDistanceMeters, 0.0)
    }

    @Test
    fun disablingGpsClearsDistanceAndBreaksPathImmediately() {
        val trip = trip()
        trip.onFix(7.47, 125.80, 10f, 1_000, 1_000)
        trip.onGpsUnavailable()
        assertNull(trip.snapshot().remainingMeters)
        trip.onFix(7.471, 125.80, 10f, 2_000, 2_000)
        assertEquals(2, trip.finish(2_000).segments.size)
    }
}
