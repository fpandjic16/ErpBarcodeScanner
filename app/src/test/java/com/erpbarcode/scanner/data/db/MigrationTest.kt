package com.erpbarcode.scanner.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Every schema version that was ever exported, taken up to the current one by the migrations the
 * app registers ([AppDatabase.ALL_MIGRATIONS]), with an operator's work in it.
 *
 * For each exported version the database is built from that version's own schema file, a document
 * with a line and a scan is written into it, and Room is asked to open it. Room runs the chain
 * and then compares the result with the entities, column by column, and refuses to open on any
 * difference; what is left for this test to check is that the scan is still there and reads right.
 *
 * Versions without an exported schema (12) cannot be started from, only passed through.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `the migrations form one unbroken chain up to the current version`() {
        val steps = AppDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((OLDEST_VERSION until DB_VERSION).map { it to it + 1 }, steps)
    }

    @Test
    fun `the current schema is exported`() {
        assertTrue("schemas/.../$DB_VERSION.json is missing: build once and commit it", schemaFile(DB_VERSION).isFile)
    }

    @Test
    fun `every exported version migrates to the current schema and keeps its scans`() {
        val versions = exportedVersions().filter { it < DB_VERSION }
        assertTrue("found no exported schema under ${schemaDir.absolutePath}", versions.isNotEmpty())
        assertEquals("the oldest exported schema", OLDEST_VERSION, versions.first())
        for (version in versions) {
            try {
                migrateFrom(version)
            } catch (failure: Throwable) {
                throw AssertionError("Migrating from version $version: ${failure.message}", failure)
            }
        }
    }

    // ── One version ────────────────────────────────────────────────────────────

    private fun migrateFrom(version: Int) {
        context.deleteDatabase(DB_NAME)
        createAndSeed(version)

        val room = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            runBlocking {
                // The first query opens the file: this is where the chain runs and where Room
                // validates what it produced.
                val header = room.documentHeaderDao().getByKey("D1", "WHSE_SHIP")
                    ?: error("the document is gone")
                assertEquals("RC7", header.rcCode)
                assertEquals("InProgress", header.docState)
                assertEquals(HEADER_MILLIS, header.creationDateTime)

                val lines = room.documentLineDao().getByDoc("D1", "WHSE_SHIP").sortedBy { it.lineNo }
                assertEquals(listOf(-1, 10000), lines.map { it.lineNo })
                // A negative number was the only mark of a line added on the device before 21.
                assertEquals(listOf(true, false), lines.map { it.addedOnDevice })
                assertEquals("111", lines[1].barcodeNo)
                assertEquals(5.0, lines[1].expected, 0.0)
                assertEquals(1.0, lines[1].scanningQty, 0.0)

                val scan = room.recordingDao().getByDoc("D1", "WHSE_SHIP").single()
                assertEquals(10000, scan.documentLine)
                assertEquals(3.0, scan.quantity, 0.0)
                assertEquals("tester", scan.userId)
                // Stored as milliseconds before 12, as ISO-8601 text since.
                assertEquals(SCAN_ISO, scan.creationDateTime)
                // Taken from the document's header when the column arrived in 15.
                assertEquals("RC7", scan.rcCode)
                assertEquals(if (version < 10) "" else "KOM", scan.unitOfMeasureCode)
                // Generated for every existing scan in 17, and kept as it was from then on.
                assertTrue("not a GUID: ${scan.recordingGuid}", GUID.matches(scan.recordingGuid))
                if (version >= 17) assertEquals(SEEDED_GUID, scan.recordingGuid)
                // Whatever was on a device before 18 had not been sent.
                assertNull(scan.sentAt)
                assertNull(scan.lastError)

                // The single COMPLAINT type was removed in 20, with its documents.
                assertEquals(listOf("D1"), room.documentHeaderDao().getAll().map { it.documentNo })
                assertTrue(room.documentLineDao().getAll().all { it.documentNo == "D1" })
                assertEquals(1, room.recordingDao().getAll().size)
            }
        } finally {
            room.close()
        }
    }

    /** Builds the database exactly as [version] created it, and puts a document with a scan in it. */
    private fun createAndSeed(version: Int) {
        val database = JsonParser.parseString(schemaFile(version).readText()).asJsonObject.getAsJsonObject("database")
        val tables = database.getAsJsonArray("entities").map { it.asJsonObject }.associateBy { it["tableName"].asString }

        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            for ((name, table) in tables) {
                sqlite.execSQL(table["createSql"].asString.replace(TABLE_NAME, name))
                table.getAsJsonArray("indices")?.forEach {
                    sqlite.execSQL(it.asJsonObject["createSql"].asString.replace(TABLE_NAME, name))
                }
            }
            database.getAsJsonArray("setupQueries").forEach { sqlite.execSQL(it.asString) }

            seedDocument(sqlite, tables, documentNo = "D1", type = "WHSE_SHIP")
            // Only where such a document could exist: before the type was split in 20.
            if (version < 20) seedDocument(sqlite, tables, documentNo = "C1", type = "COMPLAINT")

            sqlite.version = version
        } finally {
            sqlite.close()
        }
    }

    private fun seedDocument(sqlite: SQLiteDatabase, tables: Map<String, JsonObject>, documentNo: String, type: String) {
        val header = tables.getValue("documentHeader")
        val line = tables.getValue("documentLine")
        val recording = tables.getValue("recordings")

        insert(sqlite, header, mapOf(
            "documentNo" to documentNo, "type" to type, "rcCode" to "RC7", "docState" to "InProgress",
            // The column was called downloadedAt in 7.
            "downloadedAt" to HEADER_MILLIS, "creationDateTime" to HEADER_MILLIS,
        ))
        insert(sqlite, line, mapOf(
            "documentNo" to documentNo, "type" to type, "lineNo" to 10000L, "barcodeNo" to "111",
            "expected" to 5.0, "unitOfMeasureCode" to "KOM", "addedOnDevice" to 0L,
        ))
        insert(sqlite, line, mapOf(
            "documentNo" to documentNo, "type" to type, "lineNo" to -1L, "barcodeNo" to "999",
            "expected" to 0.0, "unitOfMeasureCode" to "KOM", "addedOnDevice" to 1L,
        ))
        insert(sqlite, recording, mapOf(
            "documentNo" to documentNo, "type" to type, "documentLine" to 10000L, "recordingLineNo" to 1L,
            "barcodeNo" to "111", "quantity" to 3.0, "userId" to "tester", "unitOfMeasureCode" to "KOM",
            "rcCode" to "RC7", "recordingGuid" to SEEDED_GUID,
            "creationDateTime" to if (affinityOf(recording, "creationDateTime") == "INTEGER") SCAN_MILLIS else SCAN_ISO,
        ))
    }

    /**
     * Inserts one row into [table] as that version's schema describes it. A column the version
     * has and [values] does not name gets an empty value of its type when it is NOT NULL and is
     * left NULL otherwise; a value for a column the version does not have yet is ignored.
     */
    private fun insert(sqlite: SQLiteDatabase, table: JsonObject, values: Map<String, Any>) {
        val row = ContentValues()
        for (field in table.getAsJsonArray("fields").map { it.asJsonObject }) {
            val column = field["columnName"].asString
            val notNull = field["notNull"]?.asBoolean ?: false
            val value = values[column] ?: if (notNull) emptyValue(field["affinity"].asString) else continue
            when (value) {
                is String -> row.put(column, value)
                is Long -> row.put(column, value)
                is Double -> row.put(column, value)
                else -> error("No way to store $value in $column")
            }
        }
        sqlite.insertOrThrow(table["tableName"].asString, null, row)
    }

    private fun emptyValue(affinity: String): Any = when (affinity) {
        "INTEGER" -> 0L
        "REAL" -> 1.0
        else -> ""
    }

    private fun affinityOf(table: JsonObject, column: String): String? =
        table.getAsJsonArray("fields").map { it.asJsonObject }
            .firstOrNull { it["columnName"].asString == column }
            ?.get("affinity")?.asString

    // ── The exported schemas ───────────────────────────────────────────────────

    /** `app/schemas/<database class>`, whether the tests run from the module or from the project root. */
    private val schemaDir: File by lazy {
        val relative = "schemas/${AppDatabase::class.java.name}"
        listOf(File(relative), File("app/$relative")).firstOrNull { it.isDirectory }
            ?: error("Exported schemas not found from ${File("").absolutePath}")
    }

    private fun schemaFile(version: Int) = File(schemaDir, "$version.json")

    private fun exportedVersions(): List<Int> =
        schemaDir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()

    private companion object {
        const val DB_NAME = "migration_test.db"
        const val TABLE_NAME = "\${TABLE_NAME}"
        const val OLDEST_VERSION = 7

        /** 2026-01-01T08:00:00Z, the moment of the seeded scan, in both forms it has been stored in. */
        const val SCAN_MILLIS = 1_767_254_400_000L
        const val SCAN_ISO = "2026-01-01T08:00:00Z"
        const val HEADER_MILLIS = 1_767_250_800_000L

        const val SEEDED_GUID = "0f8fad5b-d9cb-469f-a165-70867728950e"
        val GUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
