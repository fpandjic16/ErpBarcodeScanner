package com.erpbarcode.scanner.data.auth

import android.content.Context
import com.google.gson.Gson
import com.erpbarcode.scanner.data.extsystem.ExtSystemODataClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** One operator known to this device. [id] is the normalised key; [displayName] is what they typed. */
data class UserProfile(
    val id: String,
    val displayName: String,
)

/**
 * The operators this device knows: who they are, not how to let them in.
 *
 * Holds no secret at all. It used to keep a one-way PBKDF2 digest of the password so a profile
 * could be unlocked while the ERP was unreachable, but signing in without the external system is
 * not a sign-in — the ERP is now the only authority, every time — so the digest had nothing left
 * to answer and is gone. What remains is a list of names and the enrolment that says the ERP has
 * accepted each of them here at least once.
 *
 * Deliberately separate from [ExtSystemCredentialStore], which holds the NAV password in the clear
 * because NTLM cannot work without it, and **expires**. Enrolment here does not expire: the data a
 * profile owns does not expire either, and an operator must still be able to find their own unsent
 * work after their credential has lapsed.
 */
@Singleton
class UserProfileStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    // Moved off EncryptedSharedPreferences in 3.6.28 without carrying the list over, by decision.
    // An operator reappears here at their next sign-in, and because a profile's database file is
    // named after its id, they find their own documents and unsent scans exactly where they were.
    private val store by lazy {
        SecureStore(context, fileName = "user_profiles_v2", legacyFileName = "user_profiles")
    }

    private val gson = Gson()

    private fun ids(): Set<String> = store.getString(KEY_IDS)
        ?.let { runCatching { gson.fromJson(it, Array<String>::class.java).toSet() }.getOrNull() }
        .orEmpty()

    private fun displayName(id: String): String = store.getString("$id.$KEY_NAME") ?: id

    // ── Profiles ──────────────────────────────────────────────────────────────

    fun profiles(): List<UserProfile> =
        ids().sorted().map { id -> UserProfile(id = id, displayName = displayName(id)) }

    /**
     * Who is signed in — held in memory only, and deliberately so.
     *
     * Persisting it would restore the session on the next app launch, which is precisely what must
     * not happen: a device left on a shelf would open into the last operator's data with no
     * password asked. A process restart therefore means signing in again. What *is* persisted is
     * the enrolment below, so that signing in again works without the server.
     */
    @Volatile
    private var currentProfileId: String? = null

    fun currentId(): String? = currentProfileId

    fun current(): UserProfile? = currentProfileId?.let { id ->
        UserProfile(id = id, displayName = displayName(id))
    }

    fun setCurrent(id: String) { currentProfileId = id }

    /** Forgets who is signed in. Leaves every profile and its data exactly where it is. */
    fun clearCurrent() { currentProfileId = null }

    // ── Enrolment and unlock ──────────────────────────────────────────────────

    /**
     * Records that the ERP has accepted this operator on this device.
     *
     * Called only from the success branch of a sign-in, which is what makes the profile list mean
     * "people the external system has vouched for here" rather than "names somebody typed".
     * Re-running it for an operator already enrolled is harmless and keeps their display name
     * current.
     */
    fun enroll(profile: UserProfile) {
        store.edit {
            putString(KEY_IDS, gson.toJson((ids() + profile.id).sorted()))
            putString("${profile.id}.$KEY_NAME", profile.displayName)
        }
    }

    /**
     * Forgets an operator entirely: their name and their enrolment.
     *
     * Only the enrolment. Their database, credentials and settings live elsewhere and have to be
     * removed by their own owners — see `AppViewModel.deleteProfile`, which calls this **last**,
     * so a failure part-way through leaves the profile still listed and the removal repeatable
     * rather than leaving a database file nothing can reach.
     *
     * Refuses the signed-in profile. Removing the enrolment of the operator currently working
     * would leave a session with nothing behind it.
     */
    fun delete(id: String) {
        if (id == currentProfileId) return
        store.edit {
            putString(KEY_IDS, gson.toJson((ids() - id).sorted()))
            remove("$id.$KEY_NAME")
        }
    }

    companion object {
        /** Longest user name the ERP will take. Enforced before a profile or a scan can exist. */
        const val MAX_NAME_LENGTH = 50

        private const val KEY_IDS = "ids"
        private const val KEY_NAME = "name"
        // The password digests an older version kept went with the old file, which is deleted
        // unread (see SecureStore's legacyFileName).

        /**
         * The identity a profile is keyed by, and the value written into every recording's
         * `userId`.
         *
         * Upper case, domain removed. The same person reaches the same data whether they type
         * `alice`, `DOMAIN\alice` or `alice@example.com` — which they did not before: `User.id` was
         * the raw typed string, so one person could end up with three identities and, under
         * per-profile storage, three separate databases.
         *
         * Returns null for anything that cannot serve as an identity: blank, or longer than
         * [MAX_NAME_LENGTH]. Callers refuse rather than truncate — a silently shortened name is a
         * different person as far as this key is concerned.
         */
        fun normalise(raw: String): String? {
            val bare = ExtSystemODataClient.parseDomainUser(raw).second.trim().uppercase()
            return if (bare.isEmpty() || bare.length > MAX_NAME_LENGTH) null else bare
        }

        /**
         * Database filename for a profile.
         *
         * A hash, never the name itself: the key may legally contain characters a filesystem will
         * not take, and hashing means the filename can never depend on what an operator typed.
         */
        fun databaseName(id: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).toHex()
            return "user_${hash.take(16)}.db"
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
