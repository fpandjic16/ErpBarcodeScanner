package com.erpbarcode.scanner.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small key-value storage whose values are encrypted with an AES-256-GCM key kept in the Android
 * Keystore, where it cannot be read out of the device.
 *
 * It replaces androidx.security's EncryptedSharedPreferences, which Google has deprecated: no
 * fixes are coming for it, and its known failure, a Keystore that loses the key after an update or
 * a restore, crashed the app at startup instead of costing a sign-in. Here a value that no longer
 * decrypts reads as absent and is removed, and the operator signs in again.
 *
 * Names are stored as a SHA-256 digest, so the file does not list who has used the device; values
 * as `base64(iv ‖ ciphertext ‖ tag)`. Nothing read or written here is ever logged.
 *
 * No data was carried over from the old storage, by decision: the stores that use this are a
 * credential cache that expires anyway and the list of operators enrolled here, and both are
 * rebuilt by signing in. [legacyFileName] names the old file, which is deleted unread on first use.
 */
class SecureStore(
    private val context: Context,
    fileName: String,
    private val legacyFileName: String? = null,
) {
    private val prefs: SharedPreferences by lazy {
        legacyFileName?.let { legacy ->
            runCatching { context.deleteSharedPreferences(legacy) }
                .onFailure { Timber.w("Could not delete the old encrypted store") }
        }
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    fun getString(name: String): String? {
        val stored = prefs.getString(digest(name), null) ?: return null
        return decrypt(stored) ?: run {
            // Written under a key the Keystore no longer has, or damaged: as good as absent.
            prefs.edit().remove(digest(name)).apply()
            null
        }
    }

    fun getLong(name: String, default: Long): Long = getString(name)?.toLongOrNull() ?: default

    fun edit(block: Editor.() -> Unit) {
        val editor = Editor(prefs.edit())
        editor.block()
        editor.save()
    }

    inner class Editor internal constructor(private val delegate: SharedPreferences.Editor) {
        fun putString(name: String, value: String) {
            val encrypted = encrypt(value)
            if (encrypted == null) delegate.remove(digest(name)) else delegate.putString(digest(name), encrypted)
        }
        fun putLong(name: String, value: Long) = putString(name, value.toString())
        fun remove(name: String) { delegate.remove(digest(name)) }
        internal fun save() = delegate.apply()
    }

    private fun digest(name: String): String =
        MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
    }.onFailure { Timber.e("SecureStore: encryption failed (${it.javaClass.simpleName})") }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.onFailure { Timber.w("SecureStore: a value could not be decrypted (${it.javaClass.simpleName})") }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "secure_store_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        /** One key for every store; created the first time anything is written or read. */
        @Synchronized
        fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
