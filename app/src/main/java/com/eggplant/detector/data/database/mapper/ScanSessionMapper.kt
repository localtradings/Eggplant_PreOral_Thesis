package com.eggplant.detector.data.database.mapper

import com.eggplant.detector.data.catalog.DiseaseCatalog
import com.eggplant.detector.data.database.entity.ScanDetectionEntity
import com.eggplant.detector.data.database.entity.ScanSessionEntity
import com.eggplant.detector.data.database.entity.ScanSessionWithDetections
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.domain.model.DiseaseType
import com.eggplant.detector.domain.model.ScanCategory
import com.eggplant.detector.domain.model.ScanDetectionResult
import com.eggplant.detector.domain.model.ScanOutcome
import com.eggplant.detector.domain.model.ScanResult
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt

object ScanSessionMapper {
    fun toDomain(
        row: ScanSessionWithDetections,
        catalog: List<com.eggplant.detector.domain.model.Disease> = DiseaseCatalog.diseases,
    ): ScanResult {
        val mappedDetections = row.detections.sortedByDescending { it.confidence }.map { detection ->
            val disease = catalog.firstOrNull { it.id == detection.diseaseId }
                ?: DiseaseCatalog.byId(detection.diseaseId)
            ScanDetectionResult(
                id = detection.id,
                diseaseId = detection.diseaseId,
                name = disease?.name ?: detection.modelLabel,
                modelClassIndex = detection.modelClassIndex,
                modelLabel = detection.modelLabel,
                confidence = (detection.confidence * 100).roundToInt().coerceIn(0, 100),
                bounds = NormalizedBox(detection.left, detection.top, detection.right, detection.bottom),
            )
        }
        val primaryDetection = mappedDetections.firstOrNull()
        val primaryDisease = primaryDetection?.let { detection ->
            catalog.firstOrNull { it.id == detection.diseaseId } ?: DiseaseCatalog.byId(detection.diseaseId)
        }
        val category = when (primaryDisease?.type) {
            DiseaseType.LEAF_DISEASE -> ScanCategory.LEAF_DISEASE
            DiseaseType.FRUIT_DISEASE -> ScanCategory.FRUIT_DISEASE
            null -> ScanCategory.NO_DISEASE_DETECTED
        }
        return ScanResult(
            id = row.session.id,
            name = primaryDisease?.name ?: "Healthy",
            category = category,
            outcome = if (category == ScanCategory.NO_DISEASE_DETECTED) {
                ScanOutcome.HEALTHY_CONFIRMED
            } else {
                ScanOutcome.DISEASE
            },
            confidence = primaryDetection?.confidence ?: 0,
            scannedAt = LocalDateTime.parse(row.session.savedAt),
            signs = primaryDisease?.signs.orEmpty(),
            treatment = primaryDisease?.treatment.orEmpty(),
            diseaseId = primaryDisease?.id ?: "healthy",
            source = row.session.source.lowercase(),
            modelVersion = row.session.modelVersion,
            imagePath = row.session.imagePath,
            detections = mappedDetections,
            saveMode = row.session.saveMode,
        )
    }

    fun fromDomain(result: ScanResult): Pair<ScanSessionEntity, List<ScanDetectionEntity>> {
        val diseaseDetections = result.detections.filterNot { detection ->
            ModelMetadata.EGGPLANT_YOLO26M.classFor(detection.modelClassIndex)?.isHealthy == true
        }
        val persistedDetections = if (diseaseDetections.isNotEmpty()) {
            diseaseDetections
        } else {
            val modelClass = ModelMetadata.EGGPLANT_YOLO26M.classes.firstOrNull {
                it.diseaseId == result.diseaseId
            }
            if (modelClass == null) emptyList() else listOf(
                ScanDetectionResult(
                    id = "${result.id}:0",
                    diseaseId = result.diseaseId,
                    name = result.name,
                    modelClassIndex = modelClass.index,
                    modelLabel = modelClass.modelLabel,
                    confidence = result.confidence,
                    bounds = NormalizedBox(0f, 0f, 1f, 1f),
                ),
            )
        }
        val session = ScanSessionEntity(
            id = result.id,
            source = result.source.uppercase(),
            startedAt = result.scannedAt.toString(),
            savedAt = result.scannedAt.toString(),
            imagePath = result.imagePath,
            modelVersion = result.modelVersion,
            saveMode = result.saveMode,
        )
        val detections = persistedDetections.mapIndexed { index, detection ->
            ScanDetectionEntity(
                id = detection.id.ifBlank { "${result.id}:${index}:${UUID.randomUUID()}" },
                sessionId = result.id,
                diseaseId = detection.diseaseId,
                modelClassIndex = detection.modelClassIndex,
                modelLabel = detection.modelLabel,
                confidence = detection.confidence / 100f,
                left = detection.bounds.left,
                top = detection.bounds.top,
                right = detection.bounds.right,
                bottom = detection.bounds.bottom,
                detectedAt = result.scannedAt.toString(),
            )
        }
        return session to detections
    }
}
