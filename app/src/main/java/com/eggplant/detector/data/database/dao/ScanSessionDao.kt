package com.eggplant.detector.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.eggplant.detector.data.database.entity.ScanDetectionEntity
import com.eggplant.detector.data.database.entity.ScanSessionEntity
import com.eggplant.detector.data.database.entity.ScanSessionWithDetections
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Transaction
    @Query("SELECT * FROM scan_sessions ORDER BY savedAt DESC")
    fun observeSessions(): Flow<List<ScanSessionWithDetections>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: ScanSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDetections(detections: List<ScanDetectionEntity>)

    @Transaction
    suspend fun insertSessionWithDetections(
        session: ScanSessionEntity,
        detections: List<ScanDetectionEntity>,
    ) {
        require(detections.isNotEmpty()) { "Saved disease sessions require at least one detection." }
        require(detections.all { it.sessionId == session.id }) { "Every detection must belong to the session." }
        insertSession(session)
        insertDetections(detections)
    }
}
