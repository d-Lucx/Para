package com.dlucx.para

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class EncryptedFile(context: Context, name: String, private val keyAlias: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, name))

    fun delete() = file.delete()

    fun read(): String? {
        val bytes = try {
            file.readFully()
        } catch (error: FileNotFoundException) {
            if (!file.baseFile.exists()) return null
            throw error
        }
        check(bytes.size >= 1 + NonceLength + 16 && bytes[0] == Version)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(create = false),
            GCMParameterSpec(128, bytes, 1, NonceLength),
        )
        val offset = 1 + NonceLength
        return cipher.doFinal(bytes, offset, bytes.size - offset).decodeToString()
    }

    fun write(value: String) {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        check(cipher.iv.size == NonceLength)
        val bytes = byteArrayOf(Version) + cipher.iv + cipher.doFinal(value.encodeToByteArray())
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun key(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(Provider).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Provider).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {

        const val Provider = "AndroidKeyStore"

        const val Transformation = "AES/GCM/NoPadding"
        const val NonceLength = 12
        const val Version: Byte = 1
    }
}

