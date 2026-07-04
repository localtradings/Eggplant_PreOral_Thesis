package com.eggplant.detector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_records ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<ScanRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ScanRecordEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_state")
    fun observeAll(): Flow<List<NotificationStateEntity>>

    @Upsert
    suspend fun upsert(state: NotificationStateEntity)
}

@Dao
interface CatalogDao {
    @Transaction
    @Query("SELECT * FROM diseases ORDER BY modelClassIndex")
    fun observeCatalog(): Flow<List<DiseaseCatalogBundle>>

    @Upsert
    suspend fun upsertDiseases(rows: List<DiseaseEntity>)

    @Upsert
    suspend fun upsertLocalizations(rows: List<DiseaseLocalizationEntity>)

    @Upsert
    suspend fun upsertSigns(rows: List<DiseaseSignEntity>)

    @Upsert
    suspend fun upsertTreatments(rows: List<TreatmentEntity>)

    @Query("SELECT COUNT(*) FROM diseases")
    suspend fun diseaseCount(): Int

    @Transaction
    suspend fun upsertCatalog(
        diseases: List<DiseaseEntity>,
        localizations: List<DiseaseLocalizationEntity>,
        signs: List<DiseaseSignEntity>,
        treatments: List<TreatmentEntity>,
    ) {
        upsertDiseases(diseases)
        upsertLocalizations(localizations)
        upsertSigns(signs)
        upsertTreatments(treatments)
    }
}

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
