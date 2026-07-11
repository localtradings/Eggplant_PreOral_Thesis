package com.eggplant.detector.data.catalog

import com.eggplant.detector.domain.model.Disease

/**
 * Keeps Result, History, Library, and cloud-cached screens on the same
 * localized content: prefer synchronized catalog data, then use the bundled
 * fallback in the requested language.
 */
object DiseaseContentResolver {
    fun resolve(diseaseId: String, synchronizedCatalog: List<Disease>, languageTag: String): Disease? =
        synchronizedCatalog.firstOrNull { it.id == diseaseId }
            ?: DiseaseCatalog.forLanguage(languageTag).firstOrNull { it.id == diseaseId }
}
