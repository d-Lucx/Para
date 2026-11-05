package com.dlucx.para

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log

/** Explicit alarm target. Never starts or reconstructs a terminated trip. */
class BackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TripService.BackupAction) return
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.i("ParaBackup", "manifest deadline received")
        }
        TripService.deliverBackupDeadline()
    }
}
