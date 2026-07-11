package com.eggplant.detector.data.catalog

import com.eggplant.detector.data.database.entity.DiseaseEntity
import com.eggplant.detector.data.database.entity.DiseaseLocalizationEntity
import com.eggplant.detector.data.database.entity.DiseaseSignEntity
import com.eggplant.detector.data.database.entity.DiseaseReferenceEntity
import com.eggplant.detector.data.database.entity.TreatmentEntity
import com.eggplant.detector.detection.ncnn.ModelMetadata

data class DiseaseCatalogSeed(
    val diseases: List<DiseaseEntity>,
    val localizations: List<DiseaseLocalizationEntity>,
    val signs: List<DiseaseSignEntity>,
    val treatments: List<TreatmentEntity>,
    val references: List<DiseaseReferenceEntity>,
) {
    companion object {
        fun create(): DiseaseCatalogSeed {
            val modelClasses = ModelMetadata.EGGPLANT_YOLO26M.classes.filterNot { it.isHealthy }
            val diseasesByLanguage = mapOf(
                "en" to DiseaseCatalog.forLanguage("en"),
                "fil" to DiseaseCatalog.forLanguage("fil"),
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
                    val education = DiseaseEducationCatalog.get(disease.id, languageTag)
                    DiseaseLocalizationEntity(
                        diseaseId = disease.id,
                        languageTag = languageTag,
                        name = disease.name,
                        description = disease.symptomPreview,
                        symptomPreview = disease.symptomPreview,
                        prevention = disease.prevention,
                        causes = education.causes,
                        guidance = education.guidance,
                        whenToAct = education.whenToAct,
                        disclaimer = education.disclaimer,
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
            val references = diseasesByLanguage.flatMap { (languageTag, localizedDiseases) ->
                localizedDiseases.flatMap { disease ->
                    DiseaseEducationCatalog.get(disease.id, languageTag).references.mapIndexed { index, reference ->
                        DiseaseReferenceEntity(
                            diseaseId = disease.id,
                            languageTag = languageTag,
                            position = index,
                            publisher = reference.publisher,
                            title = reference.title,
                            url = reference.url,
                        )
                    }
                }
            }
            return DiseaseCatalogSeed(diseases, localizations, signs, treatments, references)
        }
    }
}
