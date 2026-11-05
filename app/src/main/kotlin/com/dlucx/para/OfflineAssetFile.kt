package com.dlucx.para

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

internal fun Context.offlineAssetFile(asset: String, name: String, expected: Long, hash: String): File {
    val file = File(noBackupFilesDir, name)
    if (file.length() != expected) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            assets.open(asset).use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    total += count
                }
                check(total == expected)
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == hash)
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
    }
    return file
}
