package com.erpbarcode.scanner.data.auth

import android.content.Context
import com.google.gson.Gson
import com.erpbarcode.scanner.data.extsystem.ExtSystemODataClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One operator known to this device, on one ERP installation.
 *
 * [id] is the normalised user name and what every recording carries as its author; [displayName]
 * is what they typed. [serverKey] is the installation that accepted them
 * (`ExtSystemConfig.serverIdentity`: server and company), [serverLabel] and [environment] are how
 * to show it. The same person signing in to two companies is two profiles, with separate data.
 */
data class UserProfile(
    val id: String,
    val displayName: String,
    val serverKey: String = "",
    val serverLabel: String = "",
    /** `ExtSystemEnvironment` name when the configuration said which system it was, else null. */
    val environment: String? = null,
) {
    /**
     * What this profile's data is filed under: the operator and the installation together.
     *
     * The database file, the saved sign-in and the enrolment all use it, so there is no path from
     * one installation to the data kept for another. See [ExtSystemConfig.serverIdentity].
     */
    val key: String get() = UserProfileStore.profileKey(id, serverKey)
}

/**
 * The operators this device knows: who they are, not how to let them in.
 *
 * Holds no secret at all. It used to keep a one-way PBKDF2 digest of the password so a profile
 * could be unlocked while the ERP was unreachable, but signing in without the external system is
 * not a sign-in — the ERP is now the only authority, every time — so the digest had nothing left
 * to answer and is gone. What remains is a list of names and the enrolment that says the ERP has
 * accepted each of them here at least once.
 *
 * **An enrolment is per installation** (3.6.53): the list holds one entry per operator per server
 * and company, because that pair is what the data is filed under.
 *
 * Deliberately separate from [ExtSystemCredentialStore], which holds the ERP password in the clear
 * because NTLM cannot work without it, and **expires**. Enrolment here does not expire: the data a
 * profile owns does not expire either, and an operator must still be able to find their own unsent
 * work after their credential has lapsed.
 */
@Singleton
class UserProfileStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    // Moved off EncryptedSharedPreferences in 3.6.28 without carrying the list over, by decision.
    // An operator reappears here at their next sign-in.
    private val store by lazy {
        SecureStore(context, fileName = "user_profiles_v2", legacyFileName = "user_profiles")
    }

    private val gson = Gson()

    /** The stored shape. Every field nullable, so a list written by another version still reads. */
    private data class StoredProfile(
        val id: String? = null,
        val name: String? = null,
        val server: String? = null,
        val label: String? = null,
        val env: String? = null,
    )

    private fun read(): List<UserProfile> = store.getString(KEY_PROFILES)
        ?.let { runCatching { gson.fromJson(it, Array<StoredProfile>::class.java).toList() }.getOrNull() }
        .orEmpty()
        .mapNotNull { stored ->
            val id = stored.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            UserProfile(
                id = id,
                displayName = stored.name ?: id,
                serverKey = stored.server.orEmpty(),
                serverLabel = stored.label.orEmpty(),
                environment = stored.env,
            )
        }

    private fun write(profiles: List<UserProfile>) {
        store.edit {
            putString(
                KEY_PROFILES,
                gson.toJson(profiles.map { StoredProfile(it.id, it.displayName, it.serverKey, it.serverLabel, it.environment) }),
            )
        }
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    /** Every enrolment on the device, on every installation; ordered by installation, then name. */
    fun profiles(): List<UserProfile> =
        read().sortedWith(compareBy({ it.serverLabel.lowercase() }, { it.serverKey }, { it.id }))

    /** The operators enrolled on one installation: the ones the sign-in screen offers. */
    fun profilesOn(serverKey: String): List<UserProfile> =
        read().filter { it.serverKey == serverKey }.sortedBy { it.id }

    /**
     * Who is signed in — held in memory only, and deliberately so.
     *
     * Persisting it would restore the session on the next app launch, which is precisely what must
     * not happen: a device left on a shelf would open into the last operator's data with no
     * password asked. A process restart therefore means signing in again.
     */
    @Volatile
    private var currentProfile: UserProfile? = null

    /** The signed-in operator's id: who they are, whichever installation they are on. */
    fun currentId(): String? = currentProfile?.id

    /** What the signed-in operator's data is filed under; see [UserProfile.key]. */
    fun currentKey(): String? = currentProfile?.key

    fun current(): UserProfile? = currentProfile

    fun setCurrent(profile: UserProfile) { currentProfile = profile }

    /** Forgets who is signed in. Leaves every profile and its data exactly where it is. */
    fun clearCurrent() { currentProfile = null }

    // ── Enrolment ─────────────────────────────────────────────────────────────

    /**
     * Records that the ERP has accepted this operator on this device, on this installation.
     *
     * Called only from the success branch of a sign-in, which is what makes the profile list mean
     * "people the external system has vouched for here" rather than "names somebody typed".
     * Re-running it for an operator already enrolled is harmless and keeps their display name and
     * the installation's label current.
     */
    fun enroll(profile: UserProfile) {
        write(read().filter { it.key != profile.key } + profile)
    }

    /**
     * Forgets one enrolment: an operator on one installation.
     *
     * Only the enrolment. The database, credentials and settings live elsewhere and have to be
     * removed by their own owners — see `AppViewModel.deleteProfile`, which calls this **last**,
     * so a failure part-way through leaves the profile still listed and the removal repeatable
     * rather than leaving a database file nothing can reach.
     *
     * Refuses the signed-in profile. Removing the enrolment of the operator currently working
     * would leave a session with nothing behind it.
     */
    fun delete(profileKey: String) {
        if (profileKey == currentKey()) return
        write(read().filter { it.key != profileKey })
    }

    companion object {
        /** Longest user name the ERP will take. Enforced before a profile or a scan can exist. */
        const val MAX_NAME_LENGTH = 50

        // One JSON list since 3.6.53. The per-id keys an earlier build wrote ("ids", "<id>.name")
        // are simply not read any more; the operators they listed enrol again at their next
        // sign-in, under the installation they sign in to.
        private const val KEY_PROFILES = "profiles"

        /**
         * The identity an operator is known by, and the value written into every recording's
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
         * An operator on an installation, as one string. The id's length goes first, so however
         * the two parts are spelled, two different pairs can never read as the same key.
         */
        fun profileKey(id: String, serverKey: String): String = "${id.length}:$id$serverKey"

        /**
         * Database filename for a profile, from its [UserProfile.key].
         *
         * A hash, never the name itself: the key may legally contain characters a filesystem will
         * not take, and hashing means the filename can never depend on what an operator typed or
         * on a server's address.
         */
        fun databaseName(profileKey: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(profileKey.toByteArray()).toHex()
            return "user_${hash.take(16)}.db"
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
