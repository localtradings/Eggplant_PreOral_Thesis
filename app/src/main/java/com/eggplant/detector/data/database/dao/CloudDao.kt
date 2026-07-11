package com.eggplant.detector.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.eggplant.detector.data.database.entity.DiseaseRequestEntity
import com.eggplant.detector.data.database.entity.DiseaseRequestPhotoEntity
import com.eggplant.detector.data.database.entity.GlobalRankingCacheEntity
import com.eggplant.detector.data.database.entity.GlobalScanCacheEntity
import com.eggplant.detector.data.database.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudDao {
    @Query("SELECT * FROM global_scan_cache ORDER BY publishedAt DESC, id DESC")
    fun observeGlobalScans(): Flow<List<GlobalScanCacheEntity>>

    @Query("SELECT * FROM global_ranking_cache ORDER BY scanCount DESC, diseaseId")
    fun observeGlobalRankings(): Flow<List<GlobalRankingCacheEntity>>

    @Query("SELECT * FROM disease_requests ORDER BY createdAt DESC")
    fun observeDiseaseRequests(): Flow<List<DiseaseRequestEntity>>

    @Query("SELECT * FROM disease_request_photos WHERE requestId = :requestId ORDER BY position")
    suspend fun requestPhotos(requestId: String): List<DiseaseRequestPhotoEntity>

    @Query("SELECT * FROM disease_requests WHERE clientRequestId = :clientRequestId LIMIT 1")
    suspend fun diseaseRequestByClientId(clientRequestId: String): DiseaseRequestEntity?

    @Query(
        """SELECT * FROM sync_outbox
            WHERE state IN ('PENDING', 'RETRY') AND nextAttemptAt <= :now
            ORDER BY CASE WHEN eventType = 'SHARING_CONSENT' THEN 0 ELSE 1 END, createdAt
            LIMIT :limit""",
    )
    suspend fun pendingEvents(now: String, limit: Int = 20): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state IN ('PENDING', 'RETRY', 'UPLOADING') AND eventType = 'GLOBAL_SHARE'")
    suspend fun pendingShareCount(): Int

    @Query("SELECT * FROM sync_outbox WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun outboxByIdempotencyKey(idempotencyKey: String): SyncOutboxEntity?

    @Query("SELECT * FROM sync_outbox WHERE id = :id LIMIT 1")
    suspend fun outboxById(id: String): SyncOutboxEntity?

    @Query("SELECT * FROM sync_outbox WHERE eventType = 'GLOBAL_SHARE' AND state IN ('PENDING', 'RETRY', 'UPLOADING')")
    suspend fun pendingShareEvents(): List<SyncOutboxEntity>

    @Upsert suspend fun upsertOutbox(row: SyncOutboxEntity)
    @Upsert suspend fun upsertGlobalScans(rows: List<GlobalScanCacheEntity>)
    @Upsert suspend fun upsertGlobalRankings(rows: List<GlobalRankingCacheEntity>)
    @Upsert suspend fun upsertDiseaseRequest(row: DiseaseRequestEntity)
    @Upsert suspend fun upsertDiseaseRequestPhotos(rows: List<DiseaseRequestPhotoEntity>)

    @Query("UPDATE disease_requests SET state = :state, uploadProgress = :progress, updatedAt = :updatedAt WHERE clientRequestId = :clientRequestId")
    suspend fun updateDiseaseRequestState(clientRequestId: String, state: String, progress: Float, updatedAt: String)

    @Query("UPDATE disease_requests SET state = :state, adminNote = :adminNote, updatedAt = :updatedAt WHERE clientRequestId = :clientRequestId")
    suspend fun updateDiseaseRequestRemoteState(clientRequestId: String, state: String, adminNote: String?, updatedAt: String)

    @Query("UPDATE disease_request_photos SET uploadState = :state WHERE requestId = :requestId")
    suspend fun updateDiseaseRequestPhotoState(requestId: String, state: String)

    @Query("DELETE FROM global_scan_cache")
    suspend fun clearGlobalScans()

    @Query("DELETE FROM global_ranking_cache")
    suspend fun clearGlobalRankings()

    @Query("UPDATE sync_outbox SET state = 'CANCELLED', updatedAt = :updatedAt WHERE eventType = 'GLOBAL_SHARE' AND state IN ('PENDING', 'RETRY', 'UPLOADING')")
    suspend fun cancelPendingShares(updatedAt: String)
}
