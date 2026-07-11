package com.eggplant.detector.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.eggplant.detector.data.database.entity.DiseaseCatalogBundle
import com.eggplant.detector.data.database.entity.DiseaseEntity
import com.eggplant.detector.data.database.entity.DiseaseLocalizationEntity
import com.eggplant.detector.data.database.entity.DiseaseReferenceEntity
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

    @Upsert
    suspend fun upsertReferences(rows: List<DiseaseReferenceEntity>)

    @Query("SELECT COUNT(*) FROM diseases")
    suspend fun diseaseCount(): Int

    @Query("DELETE FROM disease_localizations WHERE languageTag = :languageTag")
    suspend fun clearLocalizations(languageTag: String)

    @Query("DELETE FROM disease_signs WHERE languageTag = :languageTag")
    suspend fun clearSigns(languageTag: String)

    @Query("DELETE FROM disease_references WHERE languageTag = :languageTag")
    suspend fun clearReferences(languageTag: String)

    @Query("DELETE FROM treatments WHERE languageTag = :languageTag")
    suspend fun clearTreatments(languageTag: String)

    @Transaction
    suspend fun replaceLocalizedContent(
        languageTag: String,
        diseases: List<DiseaseEntity>,
        localizations: List<DiseaseLocalizationEntity>,
        signs: List<DiseaseSignEntity>,
        treatments: List<TreatmentEntity>,
        references: List<DiseaseReferenceEntity>,
    ) {
        upsertDiseases(diseases)
        clearLocalizations(languageTag)
        clearSigns(languageTag)
        clearTreatments(languageTag)
        clearReferences(languageTag)
        upsertLocalizations(localizations)
        upsertSigns(signs)
        upsertTreatments(treatments)
        upsertReferences(references)
    }

    @Transaction
    suspend fun upsertCatalog(
        diseases: List<DiseaseEntity>,
        localizations: List<DiseaseLocalizationEntity>,
        signs: List<DiseaseSignEntity>,
        treatments: List<TreatmentEntity>,
        references: List<DiseaseReferenceEntity>,
    ) {
        upsertDiseases(diseases)
        upsertLocalizations(localizations)
        upsertSigns(signs)
        upsertTreatments(treatments)
        upsertReferences(references)
    }
}
