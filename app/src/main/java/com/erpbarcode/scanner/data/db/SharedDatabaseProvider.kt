package com.erpbarcode.scanner.data.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the reference data (locations and responsibility centres) of one ERP installation.
 *
 * That data describes a site, and a site belongs to a server and a company. It used to be one
 * file for the whole device, so a device pointed at another company went on offering the first
 * company's locations until somebody refreshed them. Now there is one file per installation
 * (`ExtSystemConfig.serverIdentity`), shared by every operator who works on it: switching to
 * another company shows that company's own list, and switching back finds the first one where it
 * was, with no download needed.
 *
 * Like [DatabaseProvider], this is not something to hold on to: ask for the installation's
 * database at the moment of use, or follow the configuration and re-subscribe.
 *
 * The migration rule from `DatabaseModule` applies here as well: no destructive fallback. The
 * schema is still at version 1 and has never changed, so there is no chain yet.
 */
@Singleton
class SharedDatabaseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Kept open, for the same reason [DatabaseProvider] keeps its files open: a refresh may be writing. */
    private val open = mutableMapOf<String, SharedDatabase>()

    /**
     * The reference data of the installation with this identity. A blank identity (no server
     * configured yet) gets a file of its own, which simply stays empty.
     */
    @Synchronized
    fun forServer(serverIdentity: String): SharedDatabase = open.getOrPut(serverIdentity) {
        Room.databaseBuilder(context, SharedDatabase::class.java, fileName(serverIdentity)).build()
    }

    companion object {
        /** A hash, never the address itself, so the filename cannot depend on what a URL contains. */
        fun fileName(serverIdentity: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(serverIdentity.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return "shared_${hash.take(16)}.db"
        }
    }
}
