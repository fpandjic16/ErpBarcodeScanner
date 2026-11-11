package com.erpbarcode.scanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The schema version of [PrimaDatabase], written once. The app info shows this value; it used to
 * show a constant of its own, "7.0.0", that nobody updated while the schema went on to 19.
 */
const val PRIMA_DB_VERSION = 21

/**
 * One operator's work: their documents, lines and recordings.
 *
 * There is one of these files per profile, opened by [com.erpbarcode.scanner.data.db.DatabaseProvider].
 * That is what keeps operators apart — not a predicate on every query, but the fact that a query
 * cannot reach outside the file it runs in. It also lets two operators hold the same NAV document
 * independently, since `(documentNo, type)` only has to be unique within one person's file.
 *
 * Locations and responsibility centres deliberately live elsewhere ([SharedDatabase]): they
 * describe the site, not the person, and duplicating them per profile would force every new
 * operator to re-download them — which needs a server that may not be reachable.
 */
@Database(
    entities = [
        DocumentHeaderEntity::class,
        DocumentLineEntity::class,
        RecordingEntity::class,
    ],
    version = PRIMA_DB_VERSION,
    exportSchema = true,
)
abstract class PrimaDatabase : RoomDatabase() {
    abstract fun documentHeaderDao(): DocumentHeaderDao
    abstract fun documentLineDao(): DocumentLineDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        /**
         * Rebuilds both tables that changed shape in v8.
         *
         * `documentHeader.downloadedAt` became `creationDateTime` and gained a nullable
         * `documentDate`; `recordings` lost its `uploaded` column and that column's index. SQLite
         * on Android 8.1 (the MC3300) predates both RENAME COLUMN and DROP COLUMN, so each table
         * is recreated and copied — the same create-copy-drop-rename shape as [MIGRATION_12_13].
         *
         * Dropping `documentHeader` while `documentLine` and `recordings` still declare a
         * CASCADE foreign key to it is only safe because Room disables foreign key enforcement
         * for the duration of a migration; with enforcement on, the drop would cascade and take
         * every line and recording with it. [MIGRATION_12_13] already relies on this.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE documentHeader_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        rcCode TEXT NOT NULL,
                        ownerUserId TEXT NOT NULL,
                        creationDateTime INTEGER NOT NULL,
                        documentDate INTEGER,
                        docState TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type)
                    )
                """.trimIndent())
                // downloadedAt carries over as creationDateTime; documentDate did not exist yet,
                // and it is nullable precisely because the ERP may not supply one.
                db.execSQL("""
                    INSERT INTO documentHeader_new
                    SELECT documentNo, type, destinationCode, sourceCode, rcCode, ownerUserId,
                           downloadedAt, NULL, docState
                    FROM documentHeader
                """.trimIndent())
                db.execSQL("DROP TABLE documentHeader")
                db.execSQL("ALTER TABLE documentHeader_new RENAME TO documentHeader")

                db.execSQL("""
                    CREATE TABLE recordings_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        documentLine INTEGER NOT NULL,
                        recordingLineNo INTEGER NOT NULL,
                        barcodeNo TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        creationDateTime INTEGER NOT NULL,
                        format TEXT,
                        userId TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type, documentLine, recordingLineNo),
                        FOREIGN KEY(documentNo, type) REFERENCES documentHeader(documentNo, type) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO recordings_new
                    SELECT documentNo, type, documentLine, recordingLineNo, barcodeNo, quantity,
                           creationDateTime, format, userId, destinationCode, sourceCode
                    FROM recordings
                """.trimIndent())
                db.execSQL("DROP TABLE recordings")
                db.execSQL("ALTER TABLE recordings_new RENAME TO recordings")
                db.execSQL("CREATE INDEX index_recordings_documentNo_type ON recordings(documentNo, type)")
                db.execSQL("CREATE INDEX index_recordings_documentLine ON recordings(documentLine)")
            }
        }

        /** v9 introduced the reference-data tables; no existing data to carry over. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS locations (
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        rcCode TEXT NOT NULL,
                        PRIMARY KEY(code)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS responsibility_centers (
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        short TEXT,
                        PRIMARY KEY(code)
                    )
                """.trimIndent())
            }
        }

        /**
         * v10 added the unit of measure to lines and recordings. Existing rows get an empty
         * string: the app treats a blank UoM as "no expectation stated", which is the honest
         * description of data recorded before the field existed — inventing a unit here would
         * make old recordings claim a UoM nobody ever scanned.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentLine ADD COLUMN unitOfMeasureCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recordings ADD COLUMN unitOfMeasureCode TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentLine ADD COLUMN scanningQty REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE recordings_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        documentLine INTEGER NOT NULL,
                        recordingLineNo INTEGER NOT NULL,
                        barcodeNo TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        creationDateTime TEXT NOT NULL,
                        format TEXT,
                        userId TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        unitOfMeasureCode TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type, documentLine, recordingLineNo),
                        FOREIGN KEY(documentNo, type) REFERENCES documentHeader(documentNo, type) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO recordings_new
                    SELECT documentNo, type, documentLine, recordingLineNo, barcodeNo, quantity,
                           strftime('%Y-%m-%dT%H:%M:%SZ', CAST(creationDateTime AS REAL) / 1000.0, 'unixepoch'),
                           format, userId, destinationCode, sourceCode, unitOfMeasureCode
                    FROM recordings
                """.trimIndent())
                db.execSQL("DROP TABLE recordings")
                db.execSQL("ALTER TABLE recordings_new RENAME TO recordings")
                db.execSQL("CREATE INDEX index_recordings_documentNo_type ON recordings(documentNo, type)")
                db.execSQL("CREATE INDEX index_recordings_documentLine ON recordings(documentLine)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE documentHeader_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        rcCode TEXT NOT NULL,
                        creationDateTime INTEGER NOT NULL,
                        documentDate INTEGER,
                        docState TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO documentHeader_new
                    SELECT documentNo, type, destinationCode, sourceCode, rcCode,
                           creationDateTime, documentDate, docState
                    FROM documentHeader
                """.trimIndent())
                db.execSQL("DROP TABLE documentHeader")
                db.execSQL("ALTER TABLE documentHeader_new RENAME TO documentHeader")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentHeader ADD COLUMN isSourceRetail INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN rcCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE recordings
                    SET rcCode = (
                        SELECT rcCode FROM documentHeader
                        WHERE documentHeader.documentNo = recordings.documentNo
                          AND documentHeader.type = recordings.type
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE recordings_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        documentLine INTEGER NOT NULL,
                        recordingLineNo INTEGER NOT NULL,
                        barcodeNo TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        creationDateTime TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        unitOfMeasureCode TEXT NOT NULL,
                        rcCode TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type, documentLine, recordingLineNo),
                        FOREIGN KEY(documentNo, type) REFERENCES documentHeader(documentNo, type) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO recordings_new
                    SELECT documentNo, type, documentLine, recordingLineNo, barcodeNo, quantity,
                           creationDateTime, userId, destinationCode, sourceCode, unitOfMeasureCode, rcCode
                    FROM recordings
                """.trimIndent())
                db.execSQL("DROP TABLE recordings")
                db.execSQL("ALTER TABLE recordings_new RENAME TO recordings")
                db.execSQL("CREATE INDEX index_recordings_documentNo_type ON recordings(documentNo, type)")
                db.execSQL("CREATE INDEX index_recordings_documentLine ON recordings(documentLine)")
            }
        }

        /**
         * Gives every recording a stable identity for the ERP.
         *
         * Rows scanned before this column existed are backfilled with a generated GUID each,
         * rather than left blank: work queued by an older install is exactly the work most likely
         * to be retried, so it needs the same protection against being posted twice as anything
         * scanned afterwards. The expression is the standard SQLite v4 UUID construction —
         * random bytes with the version nibble and variant bits set — because SQLite has no
         * UUID function of its own and NAV expects a well-formed GUID.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN recordingGuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE recordings SET recordingGuid = lower(
                        hex(randomblob(4)) || '-' ||
                        hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' ||
                        substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' ||
                        hex(randomblob(6))
                    )
                """.trimIndent())
            }
        }

        /**
         * Lets a recording be marked as sent instead of deleted, and remember why it failed.
         *
         * Both columns are nullable with no backfill, and that is the correct starting state:
         * anything already on a device is queued and has not been attempted, which is exactly
         * what null means for each.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN sentAt TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN lastError TEXT")
            }
        }

        /**
         * Moves locations and responsibility centres out to [SharedDatabase].
         *
         * In practice nothing will ever run this: per-profile files are created fresh at 19, and
         * the one pre-profile database is abandoned rather than adopted. It exists because
         * schema 18 is already exported and committed, so changing 18's shape in place would make
         * the checked-in schema a lie — and because the chain has to stay complete, for the reason
         * spelled out in DatabaseModule.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS locations")
                db.execSQL("DROP TABLE IF EXISTS responsibility_centers")
            }
        }

        /**
         * Removes the COMPLAINT document type's rows; the schema itself is unchanged.
         *
         * Complaint was split into Complaint Shipment and Complaint Receipt, and nothing can tell
         * which of the two an old complaint document was. Complaints were not yet being scanned
         * in production when the split was made, so the old type's documents are dropped rather
         * than guessed at. Left in place, they would read back as warehouse shipments — the
         * fallback for a type key this build does not know.
         *
         * Child rows first, so this does not depend on the foreign keys cascading.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM recordings WHERE type = 'COMPLAINT'")
                db.execSQL("DELETE FROM documentLine WHERE type = 'COMPLAINT'")
                db.execSQL("DELETE FROM documentHeader WHERE type = 'COMPLAINT'")
            }
        }

        /**
         * Gives a line added on the device a field of its own (`addedOnDevice`) instead of
         * leaving it to be recognised by its negative number. Every line that has one now is such
         * a line: nothing else ever got a negative number.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentLine ADD COLUMN addedOnDevice INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE documentLine SET addedOnDevice = 1 WHERE lineNo < 0")
            }
        }
    }
}
