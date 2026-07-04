package com.eggplant.detector.data.catalog

import com.eggplant.detector.data.database.entity.AppSettingsEntity
import com.eggplant.detector.detection.ncnn.ModelMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiseaseCatalogSeedTest {
    @Test
    fun `catalog seed connects every disease to model localized signs and treatment`() {
        val seed = DiseaseCatalogSeed.create()
        val modelDiseases = ModelMetadata.EGGPLANT_YOLO26M.classes.filterNot { it.isHealthy }

        assertEquals(8, seed.diseases.size)
        assertEquals(16, seed.localizations.size)
        assertEquals(modelDiseases.mapNotNull { it.diseaseId }.toSet(), seed.diseases.map { it.id }.toSet())
        assertEquals(modelDiseases.map { it.index }.toSet(), seed.diseases.map { it.modelClassIndex }.toSet())
        assertEquals(modelDiseases.map { it.modelLabel }.toSet(), seed.diseases.map { it.modelLabel }.toSet())

        seed.diseases.forEach { disease ->
            assertEquals(setOf("en", "fil"), seed.localizations.filter { it.diseaseId == disease.id }.map { it.languageTag }.toSet())
            assertTrue(seed.signs.count { it.diseaseId == disease.id } >= 2)
            assertEquals(2, seed.treatments.count { it.diseaseId == disease.id })
        }
    }

    @Test
    fun `auto save is disabled by default`() {
        assertFalse(AppSettingsEntity().autoSaveEnabled)
    }
}
