package com.eggplant.detector.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.eggplant.detector.data.database.entity.DiseaseCatalogBundle
import com.eggplant.detector.data.database.entity.DiseaseEntity
import com.eggplant.detector.data.database.entity.DiseaseLocalizationEntity
import com.eggplant.detector.data.database.entity.DiseaseSignEntity
import com.eggplant.detector.data.database.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiseaseCatalogDao {
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
