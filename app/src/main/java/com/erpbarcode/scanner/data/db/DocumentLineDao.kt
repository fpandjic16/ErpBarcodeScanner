package com.erpbarcode.scanner.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentLineDao {

    @Query("SELECT * FROM documentLine WHERE documentNo = :documentNo AND type = :type AND lineNo = :lineNo")
    suspend fun getByKey(documentNo: String, type: String, lineNo: Int): DocumentLineEntity?

    @Query("SELECT * FROM documentLine WHERE documentNo = :documentNo AND type = :type")
    suspend fun getByDoc(documentNo: String, type: String): List<DocumentLineEntity>

    @Query("SELECT * FROM documentLine WHERE documentNo = :documentNo AND type = :type")
    fun observeByDoc(documentNo: String, type: String): Flow<List<DocumentLineEntity>>

    @Query("SELECT * FROM documentLine")
    fun observeAll(): Flow<List<DocumentLineEntity>>

    @Query("SELECT * FROM documentLine")
    suspend fun getAll(): List<DocumentLineEntity>

    @Upsert
    suspend fun upsertAll(lines: List<DocumentLineEntity>)

    @Query("DELETE FROM documentLine WHERE documentNo = :documentNo AND type = :type")
    suspend fun deleteAllForDoc(documentNo: String, type: String)

    @Query("DELETE FROM documentLine WHERE documentNo = :documentNo AND type = :type AND lineNo = :lineNo")
    suspend fun deleteByKey(documentNo: String, type: String, lineNo: Int)
}
