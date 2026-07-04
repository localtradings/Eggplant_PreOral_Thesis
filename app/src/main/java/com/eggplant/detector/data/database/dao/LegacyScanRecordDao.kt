package com.eggplant.detector.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eggplant.detector.data.database.entity.LegacyScanRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LegacyScanRecordDao {
    @Query("SELECT * FROM scan_records ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<LegacyScanRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: LegacyScanRecordEntity)
}
