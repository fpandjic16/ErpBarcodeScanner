package com.erpbarcode.scanner.data.repository

import androidx.room.withTransaction
import com.erpbarcode.scanner.data.db.*
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.Line
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slack for comparing quantities, which are Doubles summed across rows.
 *
 * Without it a reduction that lands exactly on the current total can leave a residue of ~1e-16
 * behind and insert or trim a row for it. Quantities are shown to five decimal places, so
 * anything this far down is arithmetic noise, not something the operator asked for.
 */
private const val QTY_EPSILON = 1e-9

/**
 * Outcome of a manual quantity edit.
 *
 * [BelowSent] exists so the refusal can be explained rather than swallowed: the operator asked
 * for a number the device cannot deliver, and needs to know the floor and why it is there.
 */
sealed interface SetQuantityResult {
    data object Applied : SetQuantityResult
    /** Refused — [sent] of this line is already in the ERP and cannot be taken back from here. */
    data class BelowSent(val sent: Double) : SetQuantityResult
}

interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>
    fun observeDocument(documentNo: String, type: String): Flow<Document?>
    /** Individual recordings for one document — what [observeDocument] sums away. */
    fun observeRecordings(documentNo: String, type: String): Flow<List<RecordingEntity>>
    /**
     * [pruneMissing] false keeps the type's other documents. A stock count downloads one batch at
     * a time, and the batches it did not ask for are not gone from the ERP.
     */
    suspend fun replaceDownloadedDocuments(type: DocumentType, docs: List<Document>, pruneMissing: Boolean = true)
    suspend fun recordScan(
        documentNo: String,
        type: String,
        lineNo: Int,
        barcodeNo: String,
        userId: String,
        quantity: Double,
    )
    /** A scan of an item the stock count does not list; see [Line.isAddedOnDevice]. */
    suspend fun recordScanNotOnDocument(
        documentNo: String,
        type: String,
        barcodeNo: String,
        unitOfMeasureCode: String,
        userId: String,
        quantity: Double,
    )
    suspend fun setLineScanned(
        documentNo: String,
        type: String,
        lineNo: Int,
        scanned: Double,
        userId: String,
    ): SetQuantityResult
    suspend fun updateDocState(documentNo: String, type: String, state: DocState)
    /**
     * Removes a document once the ERP holds everything scanned on it, and says whether it did.
     *
     * False, and the document stays, while a single scan on it is unsent: one still queued, or
     * one left without a line. The state is then recomputed from the recordings. There is no
     * unconditional delete, so no caller can remove unsent work.
     */
    suspend fun deleteDocumentIfNothingUnsent(documentNo: String, type: String): Boolean
    /**
     * The same rule for every document at once: those without an unsent scan are removed, and the
     * number of documents that had to stay is returned.
     */
    suspend fun deleteDocumentsWithNothingUnsent(): Int
    suspend fun getQueuedRecordings(documentNo: String, type: String): List<RecordingEntity>
    suspend fun getOrphanedRecordings(documentNo: String, type: String): List<RecordingEntity>
    suspend fun hasAnyRecordings(documentNo: String, type: String): Boolean
    suspend fun markRecordingSent(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)
    suspend fun recordRecordingFailure(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
        error: String,
    )
    suspend fun discardOrphanedScans(documentNo: String, type: String)
    suspend fun discardFailedScans(documentNo: String, type: String)
    suspend fun deleteQueuedRecording(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)
    suspend fun deleteDocumentRecordings(documentNo: String, type: String)
    suspend fun recoverStalePendingUploads()
}

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val provider: DatabaseProvider,
) : DocumentRepository {

    /**
     * Resolved at every use, never stored.
     *
     * There is one database per operator and it changes when they change. A field holding the
     * database — or worse, a DAO off it — would keep writing into the file of whoever was signed
     * in when this repository was constructed, silently, into a place nobody would think to look.
     *
     * Transactional methods below shadow this with a local `val db` so that one transaction
     * cannot straddle two databases.
     */
    private val db: AppDatabase get() = provider.current()

    /**
     * Flows follow the signed-in operator.
     *
     * [flatMapLatest] over the provider is what makes a profile switch reach the screens: the old
     * subscription is cancelled and a new one opens against the new file. Built once against a
     * fixed database, these would have kept streaming the previous operator's work forever.
     *
     * No profile signed in emits an empty list rather than failing, because these are collected
     * by app-wide state that outlives sign-out.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<Document>> =
        provider.database.flatMapLatest { database ->
            if (database == null) flowOf(emptyList()) else combine(
                database.documentHeaderDao().observeAllHeaders(),
                database.documentLineDao().observeAll(),
                database.recordingDao().observeAll(),
            ) { headers, lines, recordings ->
                assembleDocuments(headers, lines, recordings)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDocument(documentNo: String, type: String): Flow<Document?> =
        provider.database.flatMapLatest { database ->
            if (database == null) flowOf(null) else combine(
                database.documentHeaderDao().observeHeader(documentNo, type),
                database.documentLineDao().observeByDoc(documentNo, type),
                database.recordingDao().observeByDoc(documentNo, type),
            ) { header, lines, recordings ->
                header?.let { DocumentHeaderWithLines(it, lines, recordings).toDomain() }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecordings(documentNo: String, type: String): Flow<List<RecordingEntity>> =
        provider.database.flatMapLatest { database ->
            database?.recordingDao()?.observeByDoc(documentNo, type) ?: flowOf(emptyList())
        }

    override suspend fun replaceDownloadedDocuments(type: DocumentType, docs: List<Document>, pruneMissing: Boolean) {
        val db = provider.current()
        db.withTransaction {
            val existingHeaders = if (pruneMissing) db.documentHeaderDao().getAll().filter { it.type == type.key } else emptyList()
            val downloadedKeys = docs.map { it.documentNo }.toSet()

            // A document no longer in this download is only removed if it has no
            // recordings — recordings (real scan progress) must never be deleted.
            for (header in existingHeaders) {
                if (header.documentNo !in downloadedKeys) {
                    val hasRecordings = db.recordingDao().getByDoc(header.documentNo, header.type).isNotEmpty()
                    if (!hasRecordings) db.documentHeaderDao().deleteByKey(header.documentNo, header.type)
                }
            }

            for (doc in docs) {
                mergeDocument(db, doc, type.key)
            }
        }
    }

    /**
     * Merges a freshly downloaded document into local state without ever deleting
     * recordings. Header/lines always refresh to the downloaded values; docState is
     * always recomputed from the merged recordings.
     */
    private suspend fun mergeDocument(db: AppDatabase, doc: Document, type: String) {
        // Header first: documentLine has a foreign key to it, so a new document needs its parent
        // row before any line can be inserted. docState is corrected at the end.
        db.documentHeaderDao().upsert(doc.toEntity())
        // Lines added on the device are not the ERP's to replace: the ERP never sent them. One survives
        // until the ERP sends a line with its barcode, which is what processing the upload does. From
        // then on its scans belong on the ERP's line, sent ones included. The re-attachment below
        // only moves queued scans, so a sent one left behind would show as an orphan and leave
        // the ERP's line looking unscanned.
        val erpLineNoByBarcode = doc.lines.sortedBy { it.lineNo }
            .groupBy { it.barcodeNo }
            .mapValues { (_, sameBarcode) -> sameBarcode.first().lineNo }
        val (replacedByErp, addedOnDevice) = db.documentLineDao().getByDoc(doc.documentNo, type)
            .filter { it.addedOnDevice }
            .partition { it.barcodeNo in erpLineNoByBarcode }
        db.documentLineDao().deleteAllForDoc(doc.documentNo, type)
        db.documentLineDao().upsertAll(doc.lines.map { it.toEntity(type) } + addedOnDevice)
        val recordings = db.recordingDao().getByDoc(doc.documentNo, type)
        for (line in replacedByErp) {
            val target = erpLineNoByBarcode.getValue(line.barcodeNo)
            for (rec in recordings.filter { it.documentLine == line.lineNo }) {
                db.recordingDao().deleteByPk(doc.documentNo, type, rec.documentLine, rec.recordingLineNo)
                val nextNo = db.recordingDao().getNextRecordingLineNo(doc.documentNo, type, target)
                db.recordingDao().insert(rec.copy(documentLine = target, recordingLineNo = nextNo))
            }
        }

        // Only once the new lines are in place, so matching runs against what the ERP just sent.
        reattachOrphansByBarcode(db, doc.documentNo, type)

        // State last, from recordings as they stand after re-attachment — computing it earlier
        // would measure against line numbers that re-attachment is about to change.
        refreshStateFromRecordings(db, doc.documentNo, type)
    }

    /**
     * Moves recordings whose line has gone onto whichever line now carries the same barcode.
     *
     * This is for the ERP renumbering a line rather than removing the item: without it, every
     * recording on a renumbered line would be treated as surplus at once. What genuinely has no
     * home stays orphaned and is surfaced for review instead.
     *
     * `recordingGuid` is carried across untouched: re-attachment moves a recording's position
     * within the document, it does not create a new one, and the GUID is how that same scan stays
     * identifiable to the ERP afterwards. The row has to be deleted and re-inserted rather than
     * updated because `documentLine` is part of the primary key — `sentAt` and `lastError` ride
     * along with it, so a row already accepted stays accepted.
     *
     * Several lines sharing a barcode resolves to the lowest line number. A scan made on the
     * recording screen is spread across such lines in order (`ScanMatcher`); here there is no
     * telling which of them a scan was meant for, and the ERP groups scans by barcode anyway.
     */
    private suspend fun reattachOrphansByBarcode(db: AppDatabase, documentNo: String, type: String) {
        val orphans = db.recordingDao().getOrphansByDoc(documentNo, type)
        if (orphans.isEmpty()) return

        val lineNoByBarcode = db.documentLineDao().getByDoc(documentNo, type)
            .sortedBy { it.lineNo }
            .groupBy { it.barcodeNo }
            .mapValues { (_, sameBarcode) -> sameBarcode.first().lineNo }

        for (orphan in orphans) {
            val target = lineNoByBarcode[orphan.barcodeNo] ?: continue
            db.recordingDao().deleteByPk(documentNo, type, orphan.documentLine, orphan.recordingLineNo)
            val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, target)
            db.recordingDao().insert(orphan.copy(documentLine = target, recordingLineNo = nextNo))
        }
    }

    private fun computeStateAfterMerge(lines: List<Line>, recordings: List<RecordingEntity>): DocState {
        if (recordings.isEmpty()) return DocState.Downloaded
        // A document with no lines is not complete. `lines.all { }` is vacuously true over an
        // empty list, so a document whose lines the ERP had all removed used to be written as
        // Completed — showing an empty status chip, with upload greyed out because no line has
        // progress, and nothing able to move it again.
        if (lines.isEmpty()) return DocState.InProgress
        val scannedByLine = recordings.groupBy { it.documentLine }
            .mapValues { (_, recs) -> recs.sumOf { it.quantity } }
        val allExact = lines.all { line -> (scannedByLine[line.lineNo] ?: 0.0) == line.expected }
        return if (allExact) DocState.Completed else DocState.InProgress
    }

    override suspend fun recordScan(
        documentNo: String,
        type: String,
        lineNo: Int,
        barcodeNo: String,
        userId: String,
        quantity: Double,
    ) {
        val db = provider.current()
        db.withTransaction {
            val line = db.documentLineDao().getByKey(documentNo, type, lineNo) ?: return@withTransaction
            val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return@withTransaction
            val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, lineNo)
            db.recordingDao().insert(
                RecordingEntity(
                    documentNo = documentNo,
                    type = type,
                    documentLine = lineNo,
                    recordingLineNo = nextNo,
                    barcodeNo = barcodeNo,
                    quantity = quantity,
                    creationDateTime = Instant.now().toString(),
                    userId = userId,
                    destinationCode = line.destinationCode,
                    sourceCode = line.sourceCode,
                    unitOfMeasureCode = line.unitOfMeasureCode,
                    rcCode = header.rcCode,
                    // Generated here, at the moment the scan becomes real, and never again.
                    recordingGuid = UUID.randomUUID().toString(),
                )
            )
            advanceToInProgressIfNeeded(db, documentNo, type)
            regressFromCompletedIfNeeded(db, documentNo, type)
        }
    }

    /**
     * Records the scan on the device-added line for this barcode, creating that line on the first
     * scan.
     *
     * A second scan of the same barcode lands on the line the first one created, including one
     * that arrives before the screen has seen that line, because the lookup runs in the same
     * transaction as the insert.
     */
    override suspend fun recordScanNotOnDocument(
        documentNo: String,
        type: String,
        barcodeNo: String,
        unitOfMeasureCode: String,
        userId: String,
        quantity: Double,
    ) {
        val db = provider.current()
        db.withTransaction {
            val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return@withTransaction
            val lines = db.documentLineDao().getByDoc(documentNo, type)
            val lineNo = lines.firstOrNull { it.barcodeNo == barcodeNo }?.lineNo ?: run {
                val newLineNo = minOf(0, lines.minOfOrNull { it.lineNo } ?: 0) - 1
                db.documentLineDao().upsertAll(listOf(
                    DocumentLineEntity(
                        documentNo = documentNo,
                        type = type,
                        lineNo = newLineNo,
                        itemNo = barcodeNo,
                        itemName = "",
                        barcodeNo = barcodeNo,
                        expected = 0.0,
                        destinationCode = header.destinationCode,
                        sourceCode = header.sourceCode,
                        unitOfMeasureCode = unitOfMeasureCode,
                        addedOnDevice = true,
                    )
                ))
                newLineNo
            }
            recordScan(documentNo, type, lineNo, barcodeNo, userId, quantity)
        }
    }

    /**
     * Sets a line's total by hand — the +/- steppers and the keypad.
     *
     * Only the *queued* part of the line moves. A scan the ERP has accepted is immutable here:
     * this ERP's recordings table validates nothing and never refuses a row, so there is no
     * delete, no correction, and no way to unsend a quantity from the device. Editing one would
     * mean the device forgetting what the ERP still holds.
     *
     * So an edit below the sent quantity is **refused**, not clamped — a silent clamp would show
     * the operator a number they didn't ask for and leave them thinking the correction went
     * through. It returns [SetQuantityResult.BelowSent] with the floor, and the caller explains it.
     *
     * Within the queued part the edit is a delta, not a rewrite. Going up appends one row, the way
     * a scan would. Going down consumes the queued rows newest-first and stops, trimming the last
     * one it only partly needs; everything older survives with its own timestamp, user and GUID.
     *
     * Both halves of that replaced a single destructive overwrite. It deleted *every* recording on
     * the line and re-queued the whole new total, which on a partly-sent line sent the accepted
     * quantity a second time — recorded in the ERP as surplus — and on any line collapsed the
     * per-scan history into one row stamped with the editing operator and the current time. On a
     * shared device that quietly re-attributed the previous operator's unsent work.
     */
    override suspend fun setLineScanned(
        documentNo: String,
        type: String,
        lineNo: Int,
        scanned: Double,
        userId: String,
    ): SetQuantityResult = provider.current().let { db -> db.withTransaction {
        val sent = db.recordingDao().getSentQuantityForLine(documentNo, type, lineNo)
        if (scanned < sent) return@withTransaction SetQuantityResult.BelowSent(sent)

        // What the edit may actually move: everything above the quantity already beyond recall.
        val targetQueued = scanned - sent
        val queuedRows = db.recordingDao().getQueuedForLineNewestFirst(documentNo, type, lineNo)
        val currentQueued = queuedRows.sumOf { it.quantity }
        val delta = targetQueued - currentQueued

        when {
            // Going up appends, exactly as a scan would — the existing rows are somebody's work
            // and there is no reason for an increase to touch them.
            delta > QTY_EPSILON -> {
                val line = db.documentLineDao().getByKey(documentNo, type, lineNo)
                val header = db.documentHeaderDao().getByKey(documentNo, type)
                if (line != null && header != null) {
                    db.recordingDao().insert(
                        RecordingEntity(
                            documentNo = documentNo,
                            type = type,
                            documentLine = lineNo,
                            recordingLineNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, lineNo),
                            barcodeNo = line.barcodeNo,
                            quantity = delta,
                            creationDateTime = Instant.now().toString(),
                            userId = userId,
                            destinationCode = line.destinationCode,
                            sourceCode = line.sourceCode,
                            unitOfMeasureCode = line.unitOfMeasureCode,
                            rcCode = header.rcCode,
                            // A genuinely new recording, so a genuinely new identity.
                            recordingGuid = UUID.randomUUID().toString(),
                        )
                    )
                }
            }
            // Going down undoes the most recent scans and stops. Older rows survive untouched,
            // keeping their own time, user and GUID.
            //
            // This used to replace every queued row on the line with one aggregate stamped with
            // the editing operator and the current time. That threw away the per-scan history the
            // recordings tree now shows, and — on a shared device — re-attributed the previous
            // operator's unsent work to whoever happened to lower the number.
            delta < -QTY_EPSILON -> {
                var remaining = -delta
                for (row in queuedRows) {
                    if (remaining <= QTY_EPSILON) break
                    if (row.quantity <= remaining + QTY_EPSILON) {
                        db.recordingDao().deleteQueuedByPk(documentNo, type, lineNo, row.recordingLineNo)
                        remaining -= row.quantity
                    } else {
                        db.recordingDao().updateQueuedQuantity(
                            documentNo, type, lineNo, row.recordingLineNo, row.quantity - remaining,
                        )
                        remaining = 0.0
                    }
                }
            }
        }
        dropEmptyLinesAddedOnDevice(db, documentNo, type)
        advanceToInProgressIfNeeded(db, documentNo, type)
        regressFromCompletedIfNeeded(db, documentNo, type)
        regressToDownloadedIfNeeded(db, documentNo, type)
        SetQuantityResult.Applied
    } }

    override suspend fun updateDocState(documentNo: String, type: String, state: DocState) {
        db.documentHeaderDao().updateState(documentNo, type, state.toDbString())
    }

    /**
     * The check and the delete are one transaction. They used to be apart, with the delete
     * unconditional: a scan written between the upload reading its rows and the document being
     * removed went with the document, never having left the device.
     *
     * A document that stays is one the operator scanned on while it was being sent. Its sent rows
     * are marked and the new one is queued, so the next upload sends only that. Its state is
     * rebuilt here because a background upload leaves it as PendingUpload, which a scan does not
     * change and which no screen offers a way out of.
     */
    override suspend fun deleteDocumentIfNothingUnsent(documentNo: String, type: String): Boolean {
        val db = provider.current()
        return db.withTransaction {
            if (db.recordingDao().countUnsentByDoc(documentNo, type) > 0) {
                refreshStateFromRecordings(db, documentNo, type)
                false
            } else {
                db.documentHeaderDao().deleteByKey(documentNo, type)
                true
            }
        }
    }

    override suspend fun deleteDocumentsWithNothingUnsent(): Int {
        val db = provider.current()
        return db.withTransaction {
            db.documentHeaderDao().deleteAllWithoutUnsentScans()
            db.documentHeaderDao().count()
        }
    }

    /** What still has to go: attached to a real line, and not yet accepted by the ERP. */
    override suspend fun getQueuedRecordings(documentNo: String, type: String): List<RecordingEntity> =
        db.recordingDao().getQueuedByDoc(documentNo, type)

    override suspend fun getOrphanedRecordings(documentNo: String, type: String): List<RecordingEntity> =
        db.recordingDao().getOrphansByDoc(documentNo, type)

    /**
     * Whether anything was ever recorded here, sent or not.
     *
     * Upload needs this to tell two states apart that both leave nothing queued: a freshly
     * downloaded document, which must be left alone, and one whose rows have all been accepted,
     * which should be removed. Getting that backwards is how downloaded documents were once
     * deleted without being sent.
     */
    override suspend fun hasAnyRecordings(documentNo: String, type: String): Boolean =
        db.recordingDao().getByDoc(documentNo, type).isNotEmpty()

    override suspend fun markRecordingSent(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
    ) {
        db.recordingDao().markSent(
            documentNo, type, documentLine, recordingLineNo, Instant.now().toString(),
        )
    }

    override suspend fun recordRecordingFailure(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
        error: String,
    ) {
        db.recordingDao().recordFailure(documentNo, type, documentLine, recordingLineNo, error)
    }

    /**
     * Drops the scans the operator has reviewed and decided are surplus.
     *
     * The one place recordings are deleted without having reached the ERP, and it takes a deliberate
     * action to get here — a scan is evidence that goods were physically in someone's hands, so
     * discarding it is the operator's call to make after taking them off the pallet, never the
     * app's to make quietly during a sync.
     */
    /**
     * Drops queued rows the ERP keeps refusing, once the operator has decided to give up on them.
     *
     * The alternative is a document that can never finish: a row rejected for its own content —
     * a field too long, a wrong type — fails identically on every retry, and with the send no
     * longer stopping at the first failure it is the only thing left holding the document open.
     */
    override suspend fun discardFailedScans(documentNo: String, type: String) {
        val db = provider.current()
        db.withTransaction {
            db.recordingDao().deleteFailedByDoc(documentNo, type)
            refreshStateFromRecordings(db, documentNo, type)
        }
    }

    override suspend fun discardOrphanedScans(documentNo: String, type: String) {
        val db = provider.current()
        db.withTransaction {
            db.recordingDao().deleteOrphansByDoc(documentNo, type)
            // The document may now be back to untouched, or merely no longer blocked.
            refreshStateFromRecordings(db, documentNo, type)
        }
    }

    /** Removes a single scan the operator judged wrong. Refuses silently if it has already gone. */
    override suspend fun deleteQueuedRecording(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
    ) {
        val db = provider.current()
        db.withTransaction {
            db.recordingDao().deleteQueuedByPk(documentNo, type, documentLine, recordingLineNo)
            refreshStateFromRecordings(db, documentNo, type)
        }
    }

    /**
     * Clears a document's scans so the operator can start it again.
     *
     * Only the queued ones. This used to take everything and force the document back to
     * Downloaded, which was harmless while accepted scans were deleted the moment the ERP
     * confirmed them — there was nothing else to take. Now that they are kept, wiping them would
     * mean the device forgets what it has already sent, while the ERP still holds it. The state is
     * recomputed from whatever remains rather than assumed.
     */
    override suspend fun deleteDocumentRecordings(documentNo: String, type: String) {
        val db = provider.current()
        db.withTransaction {
            db.recordingDao().deleteQueuedForDoc(documentNo, type)
            refreshStateFromRecordings(db, documentNo, type)
        }
    }

    /**
     * A device-added line exists only to carry its scans. Once they are all gone it is a red line
     * with nothing expected, which would keep the document from ever reading as complete.
     */
    private suspend fun dropEmptyLinesAddedOnDevice(db: AppDatabase, documentNo: String, type: String) {
        val linesWithScans = db.recordingDao().getByDoc(documentNo, type).mapTo(HashSet()) { it.documentLine }
        db.documentLineDao().getByDoc(documentNo, type)
            .filter { it.addedOnDevice && it.lineNo !in linesWithScans }
            .forEach { db.documentLineDao().deleteByKey(documentNo, type, it.lineNo) }
    }

    private suspend fun refreshStateFromRecordings(db: AppDatabase, documentNo: String, type: String) {
        dropEmptyLinesAddedOnDevice(db, documentNo, type)
        val lines = db.documentLineDao().getByDoc(documentNo, type).map { it.toDomain(0.0) }
        val recordings = db.recordingDao().getByDoc(documentNo, type)
        db.documentHeaderDao().updateState(
            documentNo, type, computeStateAfterMerge(lines, recordings).toDbString(),
        )
    }

    /**
     * Clears PendingUpload left behind by an upload that never finished.
     *
     * PendingUpload is only ever set by a background upload running in `viewModelScope`. If that
     * scope is gone — process killed, battery died, the Activity finished mid-send — the upload
     * is not resumable and the flag is stale by definition, so anything still carrying it at
     * startup is stranded and needs a way out.
     *
     * State is rebuilt from what actually survived: rows still queued mean the send stopped
     * partway, so the document goes back to InProgress and can be retried; no rows left means
     * everything was accepted and only the final delete was missed, so Downloaded is the honest
     * description of what's on the device.
     *
     * Deliberately never deletes. A stale flag is not evidence a document was uploaded — the
     * cheap cost of a document lingering after a completed send is worth far less than the risk
     * of destroying work on the strength of a flag we already know is unreliable.
     */
    override suspend fun recoverStalePendingUploads() {
        val db = provider.current()
        db.withTransaction {
            db.documentHeaderDao().getAll()
                .filter { it.docState.toDocState() == DocState.PendingUpload }
                .forEach { header ->
                    val remaining = db.recordingDao().getByDoc(header.documentNo, header.type)
                    val recovered = if (remaining.isEmpty()) DocState.Downloaded else DocState.InProgress
                    db.documentHeaderDao().updateState(header.documentNo, header.type, recovered.toDbString())
                }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun assembleDocuments(
        headers: List<DocumentHeaderEntity>,
        lines: List<DocumentLineEntity>,
        recordings: List<RecordingEntity>,
    ): List<Document> {
        val linesByDoc = lines.groupBy { it.documentNo to it.type }
        val recordingsByDoc = recordings.groupBy { it.documentNo to it.type }
        return headers.map { header ->
            DocumentHeaderWithLines(
                document = header,
                lines = linesByDoc[header.documentNo to header.type] ?: emptyList(),
                recordings = recordingsByDoc[header.documentNo to header.type] ?: emptyList(),
            ).toDomain()
        }
    }

    private suspend fun advanceToInProgressIfNeeded(db: AppDatabase, documentNo: String, type: String) {
        val doc = db.documentHeaderDao().getByKey(documentNo, type)
        if (doc?.docState == DocState.Downloaded.toDbString() || doc?.docState?.startsWith("UploadFailed:") == true) {
            db.documentHeaderDao().updateState(documentNo, type, DocState.InProgress.toDbString())
        }
    }

    private suspend fun regressToDownloadedIfNeeded(db: AppDatabase, documentNo: String, type: String) {
        val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return
        if (header.docState != DocState.InProgress.toDbString()) return
        val recordings = db.recordingDao().getByDoc(documentNo, type)
        if (recordings.isEmpty()) {
            db.documentHeaderDao().updateState(documentNo, type, DocState.Downloaded.toDbString())
        }
    }

    private suspend fun regressFromCompletedIfNeeded(db: AppDatabase, documentNo: String, type: String) {
        val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return
        if (header.docState != DocState.Completed.toDbString()) return
        val lines = db.documentLineDao().getByDoc(documentNo, type)
        val recordings = db.recordingDao().getByDoc(documentNo, type)
        val scannedByLine = recordings.groupBy { it.documentLine }
            .mapValues { (_, recs) -> recs.sumOf { it.quantity } }
        val allLinesExact = lines.all { line -> (scannedByLine[line.lineNo] ?: 0.0) == line.expected }
        if (!allLinesExact) {
            db.documentHeaderDao().updateState(documentNo, type, DocState.InProgress.toDbString())
        }
    }
}