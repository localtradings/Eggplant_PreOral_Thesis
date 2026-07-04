package com.eggplant.detector.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(
    tableName = "scan_sessions",
    primaryKeys = ["id"],
    indices = [Index("savedAt")],
)
data class ScanSessionEntity(
    val id: String,
    val source: String,
    val startedAt: String,
    val savedAt: String,
    val imagePath: String?,
    val modelVersion: String,
    val saveMode: String,
)

@Entity(
    tableName = "scan_detections",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DiseaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["diseaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("diseaseId")],
)
data class ScanDetectionEntity(
    val id: String,
    val sessionId: String,
    val diseaseId: String,
    val modelClassIndex: Int,
    val modelLabel: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val detectedAt: String,
)

data class ScanSessionWithDetections(
    @Embedded val session: ScanSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val detections: List<ScanDetectionEntity>,
)
