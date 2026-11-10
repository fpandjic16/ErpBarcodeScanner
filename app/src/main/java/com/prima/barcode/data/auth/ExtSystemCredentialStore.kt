package com.prima.barcode.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credential storage, encrypted with a key held in the Android Keystore (see [SecureStore]).
 *
 * Security properties:
 *  - AES-256-GCM encryption; the key never leaves the Keystore (hardware-backed where the device has it)
 *  - App-private storage: inaccessible to other apps without root
 *  - TTL enforced on every read; stale data wiped automatically
 *  - Credentials are never logged
 *  - Domain is stored separately in ExtSystemConfigStore (not sensitive)
 *
 * **One slot per profile.** This used to be a single slot with fixed keys, so whoever signed in
 * last overwrote everyone before them, and the TTL measured the last sign-in on the device rather
 * than the last sign-in *by that person*. Keying by profile is what makes expiry mean what it
 * reads like: an operator who signs in daily stays signed in, one who appears weekly is asked
 * again, and neither affects the other.
 *
 * What lives here expires, and is the *only* thing that lets anybody in: the ERP checks the
 * password on every sign-in. [UserProfileStore] records who has been accepted here and keeps
 * no secret of its own — the two must not be merged back together.
 *
 * Moved off EncryptedSharedPreferences in 3.6.28 without carrying anything over: everyone signs in
 * once more after that update.
 */
@Singleton
class ExtSystemCredentialStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val store by lazy {
        SecureStore(context, fileName = "ext_system_credentials_v2", legacyFileName = "ext_system_credentials")
    }

    fun save(profileId: String, username: String, password: String, ttlHours: Int) {
        store.edit {
            putString("$profileId.username", username)
            putString("$profileId.password", password)
            putLong("$profileId.expiry", System.currentTimeMillis() + ttlHours * 3_600_000L)
        }
    }

    fun get(profileId: String?): ExtSystemCredentials? {
        if (profileId == null) return null
        val expiry = store.getLong("$profileId.expiry", 0L)
        if (System.currentTimeMillis() > expiry) { clear(profileId); return null }
        val username = store.getString("$profileId.username") ?: return null
        val password = store.getString("$profileId.password") ?: return null
        return ExtSystemCredentials(username = username, password = password)
    }

    fun isValid(profileId: String?): Boolean = get(profileId) != null

    /** Drops one operator's server access. Their local data and their profile are untouched. */
    fun clear(profileId: String) {
        store.edit {
            remove("$profileId.username")
            remove("$profileId.password")
            remove("$profileId.expiry")
        }
    }

}
