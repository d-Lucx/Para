package com.dlucx.para

/** Translate the remaining monotonic countdown into Android's visible alarm-clock time. */
internal fun backupWallClockTrigger(deadlineElapsed: Long, elapsedNow: Long, wallNow: Long): Long =
    wallNow + (deadlineElapsed - elapsedNow).coerceAtLeast(0L)
