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

    @Query("DELETE FROM documentHeader")
    suspend fun deleteAll()
}
