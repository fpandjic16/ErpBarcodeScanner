package com.erpbarcode.scanner.testing

import android.content.Context
import com.erpbarcode.scanner.data.auth.UserProfile
import com.erpbarcode.scanner.data.auth.UserProfileStore
import com.erpbarcode.scanner.data.db.AppDatabase
import com.erpbarcode.scanner.data.db.DatabaseProvider
import com.erpbarcode.scanner.data.db.DocumentHeaderWithLines
import com.erpbarcode.scanner.data.db.RecordingEntity
import com.erpbarcode.scanner.data.db.toDocState
import com.erpbarcode.scanner.data.db.toDomain
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.Item
import com.erpbarcode.scanner.data.model.Line
import com.erpbarcode.scanner.data.repository.DocumentRepository
import com.erpbarcode.scanner.data.repository.DocumentRepositoryImpl
import org.robolectric.RuntimeEnvironment
import java.time.Instant

/** The operator every test scans as, unless it signs somebody else in. */
internal const val TEST_USER = "tester"

/**
 * A device with an operator signed in, for tests that need the repository the way the app runs it:
 * the real provider, a real per-operator database file and the DAOs Room generated. Only usable
 * under Robolectric.
 *
 * Nothing here goes through the profile list or the credential store, so the Android Keystore,
 * which Robolectric does not have, is never touched.
 */
internal class TestDevice {
    val context: Context = RuntimeEnvironment.getApplication()
    val provider = DatabaseProvider(context, UserProfileStore(context))
    val repository: DocumentRepository = DocumentRepositoryImpl(provider)

    private val signedIn = mutableListOf<UserProfile>()

    /** The signed-in operator's database. Resolved at each use, as everywhere else. */
    val db: AppDatabase get() = provider.current()

    fun signIn(id: String = TEST_USER): UserProfile {
        val profile = UserProfile(id = id, displayName = id)
        if (signedIn.none { it.key == profile.key }) signedIn += profile
        provider.switchTo(profile)
        return profile
    }

    /** Closes and deletes every database a test opened, so no file handle outlives the test. */
    fun close() {
        provider.release()
        signedIn.forEach { provider.deleteProfileData(it.key) }
    }

    suspend fun download(
        vararg docs: Document,
        type: DocumentType = DocumentType.WAREHOUSE_SHIPMENT,
        pruneMissing: Boolean = true,
    ) = repository.replaceDownloadedDocuments(type, docs.toList(), pruneMissing)

    suspend fun scan(
        documentNo: String,
        lineNo: Int,
        barcode: String,
        quantity: Double = 1.0,
        type: DocumentType = DocumentType.WAREHOUSE_SHIPMENT,
    ) = repository.recordScan(documentNo, type.key, lineNo, barcode, TEST_USER, quantity)

    /**
     * The document as the screens get it. The same mapping `observeDocument` applies, read once
     * instead of observed.
     */
    suspend fun document(documentNo: String, type: DocumentType = DocumentType.WAREHOUSE_SHIPMENT): Document? {
        val header = db.documentHeaderDao().getByKey(documentNo, type.key) ?: return null
        return DocumentHeaderWithLines(
            document = header,
            lines = db.documentLineDao().getByDoc(documentNo, type.key),
            recordings = db.recordingDao().getByDoc(documentNo, type.key),
        ).toDomain()
    }

    suspend fun state(documentNo: String, type: DocumentType = DocumentType.WAREHOUSE_SHIPMENT): DocState? =
        db.documentHeaderDao().getByKey(documentNo, type.key)?.docState?.toDocState()

    /** A document's recordings by line, then in the order they were made. */
    suspend fun recordings(
        documentNo: String,
        type: DocumentType = DocumentType.WAREHOUSE_SHIPMENT,
    ): List<RecordingEntity> = db.recordingDao().getByDoc(documentNo, type.key)
        .sortedWith(compareBy({ it.documentLine }, { it.recordingLineNo }))
}

internal fun testLine(documentNo: String, lineNo: Int, barcode: String, expected: Double) = Line(
    documentNo = documentNo,
    lineNo = lineNo,
    item = Item("ITEM$lineNo", "Item $lineNo"),
    barcodeNo = barcode,
    expected = expected,
    scanned = 0.0,
    destinationCode = "DEST",
    sourceCode = "SRC",
    unitOfMeasureCode = "KOM",
)

/** A document as a download delivers it: lines, nothing scanned, state Downloaded. */
internal fun testDocument(
    documentNo: String,
    lines: List<Line>,
    type: DocumentType = DocumentType.WAREHOUSE_SHIPMENT,
    isSourceRetail: Boolean = false,
) = Document(
    documentNo = documentNo,
    type = type,
    destinationCode = "DEST",
    sourceCode = "SRC",
    rcCode = "RC",
    isSourceRetail = isSourceRetail,
    creationDateTime = Instant.parse("2026-01-05T08:00:00Z"),
    lines = lines,
    state = DocState.Downloaded,
)
