package com.eggplant.detector.data

import com.eggplant.detector.data.local.DiseaseEntity
import com.eggplant.detector.data.local.DiseaseLocalizationEntity
import com.eggplant.detector.data.local.DiseaseSignEntity
import com.eggplant.detector.data.local.TreatmentEntity
import com.eggplant.detector.detection.ModelMetadata

data class CatalogSeed(
    val diseases: List<DiseaseEntity>,
    val localizations: List<DiseaseLocalizationEntity>,
    val signs: List<DiseaseSignEntity>,
    val treatments: List<TreatmentEntity>,
) {
    companion object {
        fun create(): CatalogSeed {
            val modelClasses = ModelMetadata.EGGPLANT_YOLO26M.classes.filterNot { it.isHealthy }
            val diseasesByLanguage = mapOf(
                "en" to DiseaseData.forLanguage("en"),
                "fil" to DiseaseData.forLanguage("fil"),
            )
            val diseases = modelClasses.map { modelClass ->
                val diseaseId = requireNotNull(modelClass.diseaseId)
                val disease = requireNotNull(diseasesByLanguage.getValue("en").firstOrNull { it.id == diseaseId }) {
                    "Missing bundled catalog entry for $diseaseId"
                }
                DiseaseEntity(
                    id = diseaseId,
                    modelClassIndex = modelClass.index,
                    modelLabel = modelClass.modelLabel,
                    category = disease.type.name,
                    artworkKey = diseaseId,
                )
            }
            val localizations = diseasesByLanguage.flatMap { (languageTag, localizedDiseases) ->
                localizedDiseases.map { disease ->
                    DiseaseLocalizationEntity(
                        diseaseId = disease.id,
                        languageTag = languageTag,
                        name = disease.name,
                        description = disease.symptomPreview,
                        symptomPreview = disease.symptomPreview,
                        prevention = disease.prevention,
                    )
                }
            }
            val signs = diseasesByLanguage.flatMap { (languageTag, localizedDiseases) ->
                localizedDiseases.flatMap { disease ->
                    disease.signs.mapIndexed { index, sign ->
                        DiseaseSignEntity(disease.id, languageTag, index, sign)
                    }
                }
            }
            val treatments = diseasesByLanguage.flatMap { (languageTag, localizedDiseases) ->
                localizedDiseases.map { disease ->
                    TreatmentEntity(
                        diseaseId = disease.id,
                        languageTag = languageTag,
                        title = if (languageTag == "fil") "Paggamot" else "Treatment",
                        treatmentType = "RECOMMENDED_ACTION",
                        procedures = disease.treatment,
                    )
                }
            }
            return CatalogSeed(diseases, localizations, signs, treatments)
        }
    }
}
