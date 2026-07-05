package com.eggplant.detector.data.repository

import com.eggplant.detector.data.catalog.DiseaseCatalogSeed
import com.eggplant.detector.data.database.EggplantDatabase
import com.eggplant.detector.data.database.entity.AppSettingsEntity
import com.eggplant.detector.data.database.entity.DiseaseCatalogBundle
import com.eggplant.detector.data.database.entity.NotificationStateEntity
import com.eggplant.detector.data.database.mapper.ScanSessionMapper
import com.eggplant.detector.data.files.ScanSnapshotStore
import com.eggplant.detector.domain.model.Disease
import com.eggplant.detector.domain.model.DiseaseType
import com.eggplant.detector.domain.model.ScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EggplantRepository(
    private val database: EggplantDatabase,
    private val snapshotStore: ScanSnapshotStore? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogMutex = Mutex()
    private var catalogSeeded = false

    val history: Flow<List<ScanResult>> = combine(
        database.scanSessionDao().observeSessions(),
        database.catalogDao().observeCatalog(),
        database.settingsDao().observe(),
    ) { records, catalogRows, settings ->
        val languageTag = settings?.languageTag ?: "en"
        val catalog = catalogRows.map { it.toDomain(languageTag) }
        records.map { ScanSessionMapper.toDomain(it, catalog) }
    }

    val settings: Flow<AppSettingsEntity> = database.settingsDao().observe().map {
        it ?: AppSettingsEntity()
    }

    val readNotificationKeys: Flow<Set<String>> = database.notificationDao().observeAll().map { states ->
        states.filter(NotificationStateEntity::isRead).mapTo(mutableSetOf(), NotificationStateEntity::notificationKey)
    }

    init {
        scope.launch { ensureCatalog() }
    }

    fun catalog(languageTag: String): Flow<List<Disease>> =
        database.catalogDao().observeCatalog().map { rows -> rows.map { it.toDomain(languageTag) } }

    suspend fun saveScan(result: ScanResult) {
        ensureCatalog()
        val committedImage = snapshotStore?.commit(result.imagePath, result.id) ?: result.imagePath
        val (session, detections) = ScanSessionMapper.fromDomain(result.copy(imagePath = committedImage))
        try {
            database.scanSessionDao().insertSessionWithDetections(session, detections)
        } catch (error: Throwable) {
            if (committedImage != result.imagePath) snapshotStore?.removeCommitted(committedImage)
            throw error
        }
    }

    suspend fun stageSnapshot(frame: com.eggplant.detector.detection.api.RgbFrame): String? =
        kotlinx.coroutines.withContext(Dispatchers.IO) { snapshotStore?.stage(frame) }

    fun discardSnapshot(path: String?) = snapshotStore?.discard(path)

    suspend fun saveSettings(settings: AppSettingsEntity) {
        database.settingsDao().upsert(settings)
    }

    suspend fun markNotificationRead(key: String) {
        database.notificationDao().upsert(NotificationStateEntity(key, isRead = true))
    }

    suspend fun ensureCatalog() {
        catalogMutex.withLock {
            if (catalogSeeded) return
            val seed = DiseaseCatalogSeed.create()
            database.catalogDao().upsertCatalog(
                seed.diseases,
                seed.localizations,
                seed.signs,
                seed.treatments,
            )
            catalogSeeded = true
        }
    }
}

private fun DiseaseCatalogBundle.toDomain(languageTag: String): Disease {
    val normalizedLanguage = if (languageTag in setOf("fil", "tl")) "fil" else "en"
    val localization = localizations.firstOrNull { it.languageTag == normalizedLanguage }
        ?: localizations.first { it.languageTag == "en" }
    val localizedSigns = signs.filter { it.languageTag == localization.languageTag }.sortedBy { it.position }
    val treatment = treatments.firstOrNull { it.languageTag == localization.languageTag }
    return Disease(
        id = disease.id,
        name = localization.name,
        type = DiseaseType.valueOf(disease.category),
        symptomPreview = localization.symptomPreview,
        signs = localizedSigns.map { it.text },
        treatment = treatment?.procedures.orEmpty(),
        prevention = localization.prevention,
    )
}
