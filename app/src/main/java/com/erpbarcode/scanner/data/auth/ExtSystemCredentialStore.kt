package com.erpbarcode.scanner.data.auth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the device keeps of a sign-in the ERP accepted.
 *
 * [ntHash] stands in for the password (see `ntHashOf`); the password itself is not kept anywhere.
 * [verifiedAt] is the last time the ERP accepted this very sign-in, on the trusted clock: at the
 * sign-in itself, and again at every transfer it let through. [failures] counts what has gone
 * wrong in a row since.
 */
data class SavedSignIn(
    val username: String,
    val ntHash: String,
    val verifiedAt: Long,
    val failures: Int,
) {
    /** The hash is as good as the password for reaching the ERP, so it never reaches a log. */
    override fun toString(): String = "SavedSignIn(username=$username, failures=$failures)"
}

/**
 * The saved sign-in of each operator, encrypted with a key held in the Android Keystore (see
 * [SecureStore]).
 *
 * **One slot per profile**, filed under the profile key (operator and installation), so one
 * operator's sign-in says nothing about another's and one company's nothing about another
 * company's.
 *
 * **What is kept is the NT hash, never the password** (3.6.92), with the name it belongs to, when
 * the ERP last accepted it and how often it has failed since.
 *
 * **What it is good for.** Every transfer of a session signs with it. And for as many days as the
 * configuration allows, counted from [SavedSignIn.verifiedAt], it lets its operator in without the
 * ERP when they scan their sign-in code and the code carries the same password (see
 * [rememberedSignIn]). The ERP still decides every typed password and every sign-in nothing is
 * saved for.
 *
 * **How it ends.** [recordRefused] removes it at the [MAX_SAVED_SIGN_IN_FAILURES]th failure in a
 * row; signing out removes it when the configuration remembers nothing; deleting the profile
 * removes it; and a sign-in found expired is removed where it is found. There is no timer: a
 * transfer inside a running session uses what is saved however old it is, and the ERP's answer is
 * what counts.
 *
 * Nothing is carried over from the storage of earlier versions, which kept the password itself:
 * those files are deleted unread ([open]) and everybody signs in once more.
 */
@Singleton
class ExtSystemCredentialStore internal constructor(
    storage: () -> SecretStorage,
    private val clock: TrustedClock,
) {
    @Inject constructor(@ApplicationContext context: Context, clock: TrustedClock) : this(
        {
            SecureStore(
                context,
                fileName = "ext_system_sign_in_v3",
                legacyFileNames = listOf("ext_system_credentials", "ext_system_credentials_v2"),
            )
        },
        clock,
    )

    private val store by lazy(storage)
    private val gson = Gson()

    /** The stored shape. Every field nullable, so a damaged or foreign value reads as absent. */
    private data class Stored(
        @SerializedName("u") val u: String? = null,
        @SerializedName("h") val h: String? = null,
        @SerializedName("t") val t: Long? = null,
        @SerializedName("f") val f: Int? = null,
    )

    /**
     * Makes the storage, which deletes the files earlier versions left behind. Called once when
     * the app starts, so a file of passwords does not wait for the first sign-in to go.
     */
    fun open() = store.edit { }

    /** Keeps a sign-in the ERP has just accepted: the count starts over and so does the time. */
    @Synchronized
    fun save(profileKey: String, username: String, ntHash: String) {
        write(profileKey, SavedSignIn(username, ntHash, verifiedAt = clock.now(), failures = 0))
    }

    /** Everything kept for a profile, or null when nothing is. */
    @Synchronized
    fun saved(profileKey: String?): SavedSignIn? {
        if (profileKey == null) return null
        val stored = store.getString(name(profileKey))
            ?.let { runCatching { gson.fromJson(it, Stored::class.java) }.getOrNull() }
            ?: return null
        val username = stored.u?.takeIf { it.isNotBlank() } ?: return null
        val ntHash = stored.h?.takeIf { it.isNotBlank() } ?: return null
        return SavedSignIn(username, ntHash, verifiedAt = stored.t ?: 0L, failures = stored.f ?: 0)
    }

    /** What a transfer signs with, or null when the operator has to sign in first. */
    fun get(profileKey: String?): ExtSystemCredentials? =
        saved(profileKey)?.let { ExtSystemCredentials(it.username, it.ntHash) }

    fun isValid(profileKey: String?): Boolean = saved(profileKey) != null

    /** The ERP let a transfer through on this sign-in: it is good as of now. */
    @Synchronized
    fun recordAccepted(profileKey: String) {
        val saved = saved(profileKey) ?: return
        write(profileKey, saved.copy(verifiedAt = clock.now(), failures = 0))
    }

    /**
     * This sign-in failed once more. Removed at the [MAX_SAVED_SIGN_IN_FAILURES]th failure in a
     * row; returns whether this was the one that removed it.
     */
    @Synchronized
    fun recordRefused(profileKey: String): Boolean {
        val saved = saved(profileKey) ?: return false
        val failures = saved.failures + 1
        if (failures >= MAX_SAVED_SIGN_IN_FAILURES) {
            clear(profileKey)
            return true
        }
        write(profileKey, saved.copy(failures = failures))
        return false
    }

    /** Drops one operator's saved sign-in. Their local data and their profile are untouched. */
    @Synchronized
    fun clear(profileKey: String) {
        store.edit { remove(name(profileKey)) }
    }

    private fun write(profileKey: String, signIn: SavedSignIn) {
        val json = gson.toJson(Stored(signIn.username, signIn.ntHash, signIn.verifiedAt, signIn.failures))
        store.edit { putString(name(profileKey), json) }
    }

    private fun name(profileKey: String) = "$profileKey.signin"
}
