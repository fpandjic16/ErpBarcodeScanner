package com.erpbarcode.scanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type ORDER BY documentLine, recordingLineNo")
    fun observeByDoc(documentNo: String, type: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT COALESCE(MAX(recordingLineNo), 0) + 1 FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :documentLine")
    suspend fun getNextRecordingLineNo(documentNo: String, type: String, documentLine: Int): Int

    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type")
    suspend fun getByDoc(documentNo: String, type: String): List<RecordingEntity>

    /**
     * Recordings whose `documentLine` no longer matches any line of the document.
     *
     * They appear when the ERP drops a line the operator had already scanned: `mergeDocument`
     * replaces the line rows, and the CASCADE only reaches recordings through the header, so
     * these survive with nothing to attach to. Every other query here selects by document alone
     * and cannot tell them apart, which is how they ended up invisible to the UI and still
     * included in uploads.
     */
    @Query(
        """
        SELECT * FROM recordings AS r
        WHERE r.documentNo = :documentNo AND r.type = :type
          AND r.sentAt IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM documentLine AS l
            WHERE l.documentNo = r.documentNo AND l.type = r.type AND l.lineNo = r.documentLine
          )
        ORDER BY r.documentLine, r.recordingLineNo
        """
    )
    suspend fun getOrphansByDoc(documentNo: String, type: String): List<RecordingEntity>

    /**
     * What the upload should send: still attached to a real line, and not yet accepted.
     *
     * Ordered deliberately. Rows are attempted in this order and the operator is shown progress
     * against it, so it should not depend on whatever order SQLite happens to return.
     */
    @Query(
        """
        SELECT * FROM recordings AS r
        WHERE r.documentNo = :documentNo AND r.type = :type
          AND r.sentAt IS NULL
          AND EXISTS (
            SELECT 1 FROM documentLine AS l
            WHERE l.documentNo = r.documentNo AND l.type = r.type AND l.lineNo = r.documentLine
          )
        ORDER BY r.documentLine, r.recordingLineNo
        """
    )
    suspend fun getQueuedByDoc(documentNo: String, type: String): List<RecordingEntity>

    @Query(
        """
        UPDATE recordings SET sentAt = :sentAt, lastError = NULL
        WHERE documentNo = :documentNo AND type = :type
          AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo
        """
    )
    suspend fun markSent(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
        sentAt: String,
    )

    @Query(
        """
        UPDATE recordings SET lastError = :error
        WHERE documentNo = :documentNo AND type = :type
          AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo
        """
    )
    suspend fun recordFailure(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
        error: String,
    )

    /**
     * Rows the operator has reviewed and given up on — still queued, and carrying a reason they
     * failed. Never touches anything already accepted.
     */
    @Query(
        """
        DELETE FROM recordings
        WHERE documentNo = :documentNo AND type = :type
          AND sentAt IS NULL AND lastError IS NOT NULL
        """
    )
    suspend fun deleteFailedByDoc(documentNo: String, type: String)

    @Query(
        """
        DELETE FROM recordings
        WHERE documentNo = :documentNo AND type = :type
          AND sentAt IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM documentLine AS l
            WHERE l.documentNo = recordings.documentNo
              AND l.type = recordings.type
              AND l.lineNo = recordings.documentLine
          )
        """
    )
    suspend fun deleteOrphansByDoc(documentNo: String, type: String)

    @Insert
    suspend fun insert(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo")
    suspend fun deleteByPk(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)

    /**
     * Deletes one recording only while it is still queued.
     *
     * The `sentAt IS NULL` clause is the rule itself, not a convenience: a scan the ERP has
     * already accepted cannot be taken back from here, and removing it locally would only cost
     * the device its record of what it sent. Enforced in the query so no caller can bypass it.
     */
    @Query(
        """
        DELETE FROM recordings
        WHERE documentNo = :documentNo AND type = :type
          AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo
          AND sentAt IS NULL
        """
    )
    suspend fun deleteQueuedByPk(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)

    /** Clears a document's queued scans, leaving anything already accepted by the ERP in place. */
    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type AND sentAt IS NULL")
    suspend fun deleteQueuedForDoc(documentNo: String, type: String)

    /**
     * One line's queued scans, newest first — the order a manual reduction consumes them in.
     *
     * Lowering a quantity means undoing the most recent scans, so the newest rows go first and
     * the older ones survive untouched, keeping their own timestamp, user and GUID.
     */
    @Query("""
        SELECT * FROM recordings
        WHERE documentNo = :documentNo AND type = :type AND documentLine = :lineNo
          AND sentAt IS NULL
        ORDER BY recordingLineNo DESC
        """)
    suspend fun getQueuedForLineNewestFirst(documentNo: String, type: String, lineNo: Int): List<RecordingEntity>

    /**
     * Trims a queued scan that a reduction only partly consumed.
     *
     * Deliberately keeps the row's identity — its GUID, user and timestamp. The row has never
     * been sent, so the ERP has never seen that GUID, and it is still the same person's scan;
     * only part of it was taken back.
     */
    @Query("""
        UPDATE recordings SET quantity = :quantity
        WHERE documentNo = :documentNo AND type = :type
          AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo
          AND sentAt IS NULL
        """)
    suspend fun updateQueuedQuantity(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
        quantity: Double,
    )

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :lineNo AND sentAt IS NOT NULL")
    suspend fun getSentQuantityForLine(documentNo: String, type: String, lineNo: Int): Double

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type")
    suspend fun deleteAllForDoc(documentNo: String, type: String)

    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<RecordingEntity>
}
