package com.erpbarcode.scanner.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Named secrets, kept so that reading the file they are in gives nothing away.
 *
 * [SecureStore] is the one the app runs on. The interface is there for the tests: the Android
 * Keystore does not exist under Robolectric, and what is built on this storage (the saved
 * sign-in, its count of failures) is logic worth testing without it.
 */
interface SecretStorage {
    fun getString(name: String): String?
    fun edit(block: SecretEditor.() -> Unit)
}

/** One change to a [SecretStorage], applied when the block that receives it ends. */
interface SecretEditor {
    fun putString(name: String, value: String)
    fun remove(name: String)
}

/** Where the key that encrypts a [SecureStore] is held. */
enum class KeyProtection {
    /** In the device's secure hardware (a trusted execution environment or a secure element). */
    SECURE_HARDWARE,

    /** In software only: whoever takes over the system can read it. */
    SOFTWARE,

    /** The device would not say. Treated as not protected. */
    UNKNOWN,
}

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
 * No data is carried over from an earlier storage, by decision: the stores that use this are the
 * saved sign-in and the list of operators enrolled here, and both are rebuilt by signing in.
 * [legacyFileNames] are the files of earlier versions, deleted unread when the store is made.
 */
class SecureStore(
    private val context: Context,
    fileName: String,
    legacyFileNames: List<String> = emptyList(),
) : SecretStorage {

    init {
        // When the store is made, not when it is first read: one of these files holds passwords
        // in a form this version no longer keeps, and it must not wait for a first use to go.
        legacyFileNames.forEach { legacy ->
            runCatching { context.deleteSharedPreferences(legacy) }
                .onFailure { Timber.w("Could not delete an old encrypted store") }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    override fun getString(name: String): String? {
        val stored = prefs.getString(digest(name), null) ?: return null
        return decrypt(stored) ?: run {
            // Written under a key the Keystore no longer has, or damaged: as good as absent.
            prefs.edit().remove(digest(name)).apply()
            null
        }
    }

    override fun edit(block: SecretEditor.() -> Unit) {
        val editor = Editor(prefs.edit())
        editor.block()
        editor.save()
    }

    private inner class Editor(private val delegate: SharedPreferences.Editor) : SecretEditor {
        override fun putString(name: String, value: String) {
            val encrypted = encrypt(value)
            if (encrypted == null) delegate.remove(digest(name)) else delegate.putString(digest(name), encrypted)
        }
        override fun remove(name: String) { delegate.remove(digest(name)) }
        fun save() = delegate.apply()
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

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "secure_store_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        /** One key for every store; created the first time anything is written or read. */
        @Synchronized
        private fun key(): SecretKey {
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

        /**
         * Where this device holds the storage key: asked of the key itself, not assumed from the
         * model.
         *
         * A sign-in is remembered only where the answer is [KeyProtection.SECURE_HARDWARE]. The
         * saved sign-in is as good as the password for reaching the ERP, and a key held in
         * software is one a rooted or compromised system can copy, taking every saved sign-in on
         * the device with it. The same answer is shown under System info and About the app, which
         * is how a model is checked before it is put to work.
         */
        fun keyProtection(): KeyProtection = runCatching {
            val key = key()
            val info = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE)
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                    KeyProperties.SECURITY_LEVEL_STRONGBOX,
                    KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> KeyProtection.SECURE_HARDWARE
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeyProtection.SOFTWARE
                    else -> KeyProtection.UNKNOWN
                }
            } else if (info.inSecureHardwareBeforeApi31()) {
                KeyProtection.SECURE_HARDWARE
            } else {
                KeyProtection.SOFTWARE
            }
        }.onFailure { Timber.w("SecureStore: the key would not say where it is held (${it.javaClass.simpleName})") }
            .getOrDefault(KeyProtection.UNKNOWN)

        /** The only way to ask below API 31, where `securityLevel` does not exist yet. */
        @Suppress("DEPRECATION")
        private fun KeyInfo.inSecureHardwareBeforeApi31(): Boolean = isInsideSecureHardware
    }
}
