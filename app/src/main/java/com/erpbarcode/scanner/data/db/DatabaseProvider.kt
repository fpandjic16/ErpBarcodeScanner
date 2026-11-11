package com.erpbarcode.scanner.data.db

import android.content.Context
import androidx.room.Room
import com.erpbarcode.scanner.data.auth.UserProfile
import com.erpbarcode.scanner.data.auth.UserProfileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the database of whoever is signed in.
 *
 * One file per operator **per ERP installation** (`UserProfile.key`): the same person on another
 * server or company opens another file, so work scanned for one can never be sent to, or merged
 * with, the other.
 *
 * This is the whole of the per-user isolation. Every query in the app is scoped by
 * `(documentNo, type)` and nothing more, which is safe precisely because a query cannot reach
 * outside the file it runs in. Nobody filters by user; the file does it.
 *
 * **Never hold a DAO, or the database, in a field.** Read [current] at the moment of use, or
 * observe [database] and re-subscribe. A reference captured before a profile switch writes into
 * the previous operator's file — the one failure mode this design has, and it is silent.
 */
@Singleton
class DatabaseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileStore: UserProfileStore,
) {

    /**
     * Kept open rather than closed on switch.
     *
     * A background upload outlives the screen that started it, so a switch can land while rows
     * are still being written. Closing underneath that throws, and the write is lost — and those
     * rows are the only copy of a shift's work until the ERP has them. A handful of idle SQLite
     * handles is the cheaper side of that trade; there are as many as there are operators who
     * used this device since it last booted.
     */
    private val open = mutableMapOf<String, AppDatabase>()

    /**
     * Starts null on every process launch, because sign-in is required on every process launch —
     * [UserProfileStore] keeps the current profile in memory precisely so that a device left on a
     * shelf cannot come back up inside the last operator's data.
     */
    private val _database = MutableStateFlow<AppDatabase?>(null)

    /** Null until somebody signs in. Emits again on every profile switch. */
    val database: StateFlow<AppDatabase?> = _database.asStateFlow()

    /**
     * The database to act on right now.
     *
     * Throws when nobody is signed in. That is deliberate: every write path is reached from a
     * screen that only exists behind sign-in, so a null here is a routing bug, and failing loudly
     * beats writing an operator's scans somewhere nobody will look for them.
     */
    fun current(): AppDatabase =
        _database.value ?: error("No profile is signed in; there is no database to use")

    fun switchTo(profile: UserProfile) {
        profileStore.setCurrent(profile)
        _database.value = openFor(profile.key)
    }

    /** Signs out without touching a byte of anyone's data. */
    fun release() {
        profileStore.clearCurrent()
        _database.value = null
    }

    /**
     * Opens a profile's database without signing in as them.
     *
     * Exists for the two things that have to read or remove another operator's data from inside
     * someone else's session: counting what a deletion would destroy, and the deletion itself.
     * Nothing else should call it, and nothing may hold what it returns — the rule in this class's
     * header applies here with the usual force.
     */
    fun forProfile(profileKey: String): AppDatabase = openFor(profileKey)

    /**
     * Deletes a profile's database file and everything in it. Not undoable.
     *
     * Refuses the signed-in profile: queries, Flows and any background upload all resolve through
     * [current], and pulling the file out from under them would fail in places nothing checks.
     * `deleteDatabase` takes the `-wal` and `-shm` files with it, which a plain file delete would
     * leave behind to be found by the next database of the same name.
     */
    fun deleteProfileData(profileKey: String) {
        if (profileKey == profileStore.currentKey()) return
        open.remove(profileKey)?.close()
        context.deleteDatabase(UserProfileStore.databaseName(profileKey))
    }

    private fun openFor(profileKey: String): AppDatabase = open.getOrPut(profileKey) {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            UserProfileStore.databaseName(profileKey),
        ).addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
    }
}
