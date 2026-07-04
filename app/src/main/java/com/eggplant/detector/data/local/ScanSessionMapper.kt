package com.eggplant.detector.data.local

import com.eggplant.detector.data.DiseaseData
import com.eggplant.detector.detection.ModelMetadata
import com.eggplant.detector.detection.NormalizedBox
import com.eggplant.detector.model.DiseaseType
import com.eggplant.detector.model.ScanCategory
import com.eggplant.detector.model.ScanDetectionResult
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt

object ScanSessionMapper {
    fun toDomain(
        row: ScanSessionWithDetections,
        catalog: List<com.eggplant.detector.model.Disease> = DiseaseData.diseases,
    ): ScanResult {
        val mappedDetections = row.detections.sortedByDescending { it.confidence }.map { detection ->
            val disease = catalog.firstOrNull { it.id == detection.diseaseId }
                ?: DiseaseData.byId(detection.diseaseId)
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
            catalog.firstOrNull { it.id == detection.diseaseId } ?: DiseaseData.byId(detection.diseaseId)
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
        val persistedDetections = if (result.detections.isNotEmpty()) {
            result.detections
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
