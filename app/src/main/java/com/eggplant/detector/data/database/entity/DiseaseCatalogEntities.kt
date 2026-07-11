package com.eggplant.detector.data.database.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(
    tableName = "diseases",
    indices = [
        Index(value = ["modelClassIndex"], unique = true),
        Index(value = ["modelLabel"], unique = true),
    ],
    primaryKeys = ["id"],
)
data class DiseaseEntity(
    val id: String,
    val modelClassIndex: Int,
    val modelLabel: String,
    val category: String,
    val artworkKey: String,
)

@Entity(
    tableName = "disease_localizations",
    primaryKeys = ["diseaseId", "languageTag"],
    foreignKeys = [
        ForeignKey(
            entity = DiseaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["diseaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("diseaseId")],
)
data class DiseaseLocalizationEntity(
    val diseaseId: String,
    val languageTag: String,
    val name: String,
    val description: String,
    val symptomPreview: String,
    val prevention: String,
    @ColumnInfo(defaultValue = "''") val causes: String = "",
    @ColumnInfo(defaultValue = "''") val guidance: String = "",
    @ColumnInfo(defaultValue = "''") val whenToAct: String = "",
    @ColumnInfo(defaultValue = "''") val disclaimer: String = "",
)

@Entity(
    tableName = "disease_references",
    primaryKeys = ["diseaseId", "languageTag", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DiseaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["diseaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("diseaseId")],
)
data class DiseaseReferenceEntity(
    val diseaseId: String,
    val languageTag: String,
    val position: Int,
    val publisher: String,
    val title: String,
    val url: String,
)

@Entity(
    tableName = "disease_signs",
    primaryKeys = ["diseaseId", "languageTag", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DiseaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["diseaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("diseaseId")],
)
data class DiseaseSignEntity(
    val diseaseId: String,
    val languageTag: String,
    val position: Int,
    val text: String,
)

@Entity(
    tableName = "treatments",
    primaryKeys = ["diseaseId", "languageTag"],
    foreignKeys = [
        ForeignKey(
            entity = DiseaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["diseaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("diseaseId")],
)
data class TreatmentEntity(
    val diseaseId: String,
    val languageTag: String,
    val title: String,
    val treatmentType: String,
    val procedures: String,
)

data class DiseaseCatalogBundle(
    @Embedded val disease: DiseaseEntity,
    @Relation(parentColumn = "id", entityColumn = "diseaseId")
    val localizations: List<DiseaseLocalizationEntity>,
    @Relation(parentColumn = "id", entityColumn = "diseaseId")
    val signs: List<DiseaseSignEntity>,
    @Relation(parentColumn = "id", entityColumn = "diseaseId")
    val treatments: List<TreatmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "diseaseId")
    val references: List<DiseaseReferenceEntity>,
)
