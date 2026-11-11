package com.erpbarcode.scanner.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentHeaderDao {

    @Query("SELECT * FROM documentHeader WHERE documentNo = :documentNo AND type = :type")
    fun observeHeader(documentNo: String, type: String): Flow<DocumentHeaderEntity?>

    @Query("SELECT * FROM documentHeader")
    fun observeAllHeaders(): Flow<List<DocumentHeaderEntity>>

    @Query("SELECT * FROM documentHeader WHERE documentNo = :documentNo AND type = :type")
    suspend fun getByKey(documentNo: String, type: String): DocumentHeaderEntity?

    @Query("SELECT * FROM documentHeader")
    suspend fun getAll(): List<DocumentHeaderEntity>

    @Upsert
    suspend fun upsert(doc: DocumentHeaderEntity)

    @Query("UPDATE documentHeader SET docState = :state WHERE documentNo = :documentNo AND type = :type")
    suspend fun updateState(documentNo: String, type: String, state: String)

    @Query("DELETE FROM documentHeader WHERE documentNo = :documentNo AND type = :type")
    suspend fun deleteByKey(documentNo: String, type: String)

    /**
     * Removes every document that holds no scan the ERP has not accepted, and says how many went.
     *
     * There is no "delete everything" beside it, on purpose: a document with a scan that has not
     * been sent stays until that scan is sent or the operator deletes it. The rule is in the
     * statement itself so that no caller can remove unsent work through this.
     */
    @Query(
        """
        DELETE FROM documentHeader
        WHERE NOT EXISTS (
            SELECT 1 FROM recordings AS r
            WHERE r.documentNo = documentHeader.documentNo AND r.type = documentHeader.type
              AND r.sentAt IS NULL
        )
        """
    )
    suspend fun deleteAllWithoutUnsentScans(): Int

    @Query("SELECT COUNT(*) FROM documentHeader")
    suspend fun count(): Int
}
