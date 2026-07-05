package com.eggplant.detector.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eggplant.detector.data.catalog.DiseaseCatalog
import com.eggplant.detector.domain.model.ScanCategory
import com.eggplant.detector.domain.model.ScanResult
import java.time.LocalDateTime

@Entity(tableName = "scan_records")
data class LegacyScanRecordEntity(
    @PrimaryKey val id: String,
    val diseaseId: String,
    val category: String,
    val confidence: Int,
    val scannedAt: String,
    val source: String,
    val modelVersion: String,
) {
    fun toDomain(): ScanResult {
        val disease = DiseaseCatalog.byId(diseaseId)
        val categoryValue = ScanCategory.valueOf(category)
        return ScanResult(
            id = id,
            name = disease?.name ?: if (categoryValue == ScanCategory.NO_DISEASE_DETECTED) "Healthy" else "Unknown",
            category = categoryValue,
            confidence = confidence,
            scannedAt = LocalDateTime.parse(scannedAt),
            signs = disease?.signs.orEmpty(),
            treatment = disease?.treatment.orEmpty(),
            diseaseId = diseaseId,
            source = source,
            modelVersion = modelVersion,
        )
    }

    companion object {
        fun fromDomain(result: ScanResult): LegacyScanRecordEntity = LegacyScanRecordEntity(
            id = result.id,
            diseaseId = result.diseaseId,
            category = result.category.name,
            confidence = result.confidence,
            scannedAt = result.scannedAt.toString(),
            source = result.source,
            modelVersion = result.modelVersion,
        )
    }
}
