package com.erpbarcode.scanner.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name ASC")
    fun observeLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM responsibility_centers ORDER BY name ASC")
    fun observeRcs(): Flow<List<ResponsibilityCenterEntity>>

    @Upsert
    suspend fun upsertLocations(locations: List<LocationEntity>)

    @Upsert
    suspend fun upsertRcs(rcs: List<ResponsibilityCenterEntity>)

    @Query("DELETE FROM locations")
    suspend fun clearLocations()

    @Query("DELETE FROM responsibility_centers")
    suspend fun clearRcs()
}
