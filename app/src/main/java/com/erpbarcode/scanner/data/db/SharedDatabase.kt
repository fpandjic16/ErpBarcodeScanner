package com.erpbarcode.scanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Reference data that belongs to the site, not to an operator.
 *
 * Locations and responsibility centres used to sit in [AppDatabase]. Once that became one file
 * per profile they would have been duplicated per person, and each new operator would have had to
 * re-download them before they could pick a location — which needs a reachable server, exactly
 * the thing that cannot be assumed on a handheld in a warehouse.
 *
 * Deliberately has no migration chain yet: it is created fresh at version 1 alongside the first
 * per-profile database, and nothing has ever shipped under this filename. The moment its shape
 * changes it needs one, on the same terms as [AppDatabase] — see `DatabaseModule`.
 */
@Database(
    entities = [
        LocationEntity::class,
        ResponsibilityCenterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SharedDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
}
