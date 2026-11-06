package com.dlucx.para

import android.content.Context
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

private val offlineAssetLock = Any()

@SuppressLint("LogNotTimber") // ADB reads this debug-only metric directly.
internal fun Context.offlineAssetFile(asset: String, name: String, expected: Long, hash: String): File = synchronized(offlineAssetLock) {
    val file = File(noBackupFilesDir, name)
    if (!file.isFile || file.length() != expected) {
        val started = SystemClock.elapsedRealtime()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            assets.open(asset).use { raw ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                (if (asset.endsWith(".gz")) GZIPInputStream(raw) else raw).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= expected)
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
                check(total == expected)
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == hash)
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
        check(file.isFile && file.length() == expected) { "Could not publish offline asset $asset" }
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.i("ParaMapPerf", "copy asset=$asset bytes=$expected ms=${SystemClock.elapsedRealtime() - started}")
        }
    }
    file
}
