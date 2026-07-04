package com.eggplant.detector

import com.eggplant.detector.data.local.ScanRecordEntity
import com.eggplant.detector.data.local.ScanDetectionEntity
import com.eggplant.detector.data.local.ScanSessionEntity
import com.eggplant.detector.data.local.ScanSessionMapper
import com.eggplant.detector.data.local.ScanSessionWithDetections
import com.eggplant.detector.data.DiseaseData
import com.eggplant.detector.model.ScanCategory
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistenceMappingTest {
    @Test
    fun `scan records preserve model-ready fields`() {
        val scan = ScanResult(
            id = "scan-42",
            name = "Leaf Spot",
            category = ScanCategory.LEAF_DISEASE,
            confidence = 87,
            scannedAt = LocalDateTime.of(2026, 7, 3, 14, 30),
            signs = emptyList(),
            treatment = "",
            diseaseId = "leaf-spot",
            source = "camera",
            modelVersion = "legacy-model",
        )

        val restored = ScanRecordEntity.fromDomain(scan).toDomain()

        assertEquals(scan.id, restored.id)
        assertEquals("leaf-spot", restored.diseaseId)
        assertEquals("camera", restored.source)
        assertEquals("legacy-model", restored.modelVersion)
        assertEquals(scan.scannedAt, restored.scannedAt)
    }

    @Test
    fun `grouped session maps highest confidence disease and retains every box`() {
        val session = ScanSessionWithDetections(
            session = ScanSessionEntity(
                id = "session-42",
                source = "LIVE",
                startedAt = "2026-07-04T10:00:00",
                savedAt = "2026-07-04T10:00:02",
                imagePath = "/private/session-42.jpg",
                modelVersion = "eggplant-yolo26m-b96-20260704",
                saveMode = "MANUAL",
            ),
            detections = listOf(
                ScanDetectionEntity("d1", "session-42", "leaf-spot", 5, "Leaf-Spot", 0.87f, 0.1f, 0.2f, 0.5f, 0.7f, "2026-07-04T10:00:02"),
                ScanDetectionEntity("d2", "session-42", "wilt", 9, "Wilt", 0.78f, 0.5f, 0.2f, 0.9f, 0.8f, "2026-07-04T10:00:02"),
            ),
        )

        val result = ScanSessionMapper.toDomain(session)

        assertEquals("Leaf Spot", result.name)
        assertEquals(87, result.confidence)
        assertEquals("/private/session-42.jpg", result.imagePath)
        assertEquals(listOf("leaf-spot", "wilt"), result.detections.map { it.diseaseId })
    }

    @Test
    fun `grouped history uses the Room catalog language supplied by the repository`() {
        val session = ScanSessionWithDetections(
            session = ScanSessionEntity("session-fil", "LIVE", "2026-07-04T10:00:00", "2026-07-04T10:00:02", null, "model", "AUTO"),
            detections = listOf(
                ScanDetectionEntity("d1", "session-fil", "white-molds", 8, "White-Mold", .9f, .1f, .1f, .8f, .8f, "2026-07-04T10:00:02"),
            ),
        )

        val result = ScanSessionMapper.toDomain(session, DiseaseData.forLanguage("fil"))

        assertEquals("Puting Amag", result.name)
        assertEquals("Puting Amag", result.detections.single().name)
    }
}
