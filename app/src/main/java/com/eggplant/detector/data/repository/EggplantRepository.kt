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
import com.eggplant.detector.domain.model.ShareEligibility
import com.eggplant.detector.domain.model.GlobalScan
import com.eggplant.detector.domain.model.GlobalRanking
import com.eggplant.detector.domain.model.DiseaseRequest
import com.eggplant.detector.domain.model.DiseaseReference
import com.eggplant.detector.domain.model.CloudDeletionState
import com.eggplant.detector.domain.model.GlobalFeedState
import com.eggplant.detector.domain.model.SyncOutboxEvent
import com.eggplant.detector.domain.model.SyncOutboxState
import com.eggplant.detector.data.cloud.diseaseRequestPayload
import com.eggplant.detector.data.cloud.globalSharePayload
import com.eggplant.detector.data.cloud.sharingConsentPayload
import com.eggplant.detector.data.cloud.SharePhotoRevalidator
import com.eggplant.detector.data.database.entity.DiseaseRequestEntity
import com.eggplant.detector.data.database.entity.DiseaseRequestPhotoEntity
import com.eggplant.detector.data.database.entity.SyncOutboxEntity
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction

class EggplantRepository(
    private val database: EggplantDatabase,
    private val snapshotStore: ScanSnapshotStore? = null,
    private val cloudSync: (() -> Unit)? = null,
    private val cloudSyncLoadMore: (() -> Unit)? = null,
    private val cloudConfigured: (() -> Boolean)? = null,
    private val sharePhotoRevalidator: SharePhotoRevalidator? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogMutex = Mutex()
    private val sharingMutex = Mutex()
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

    val globalScans: Flow<List<GlobalScan>> = combine(
        database.cloudDao().observeGlobalScans(),
        database.catalogDao().observeCatalog(),
        database.settingsDao().observe(),
    ) { cached, catalogRows, settings ->
        val languageTag = settings?.languageTag ?: "en"
        val catalog = catalogRows.map { it.toDomain(languageTag) }
        cached.mapNotNull { row -> row.toDomain(catalog) }
    }

    val globalRankings: Flow<List<GlobalRanking>> = combine(
        database.cloudDao().observeGlobalRankings(),
        database.catalogDao().observeCatalog(),
        database.settingsDao().observe(),
    ) { cached, catalogRows, settings ->
        val catalog = catalogRows.map { it.toDomain(settings?.languageTag ?: "en") }
        cached.map { ranking ->
            GlobalRanking(ranking.diseaseId, catalog.firstOrNull { it.id == ranking.diseaseId }?.name ?: ranking.diseaseId, ranking.scanCount)
        }
    }

    val diseaseRequests: Flow<List<DiseaseRequest>> = database.cloudDao().observeDiseaseRequestsWithPhotos().map { requests ->
        requests.map { requestWithPhotos ->
            val request = requestWithPhotos.request
            DiseaseRequest(
                id = request.clientRequestId,
                requestedName = request.requestedName,
                notes = request.notes,
                status = request.state,
                photoPaths = requestWithPhotos.photos.map(DiseaseRequestPhotoEntity::localPhotoPath),
                adminNote = request.adminNote,
                createdAt = request.createdAt,
                uploadProgress = request.uploadProgress,
            )
        }
    }

    val globalFeedState: Flow<GlobalFeedState> = database.cloudDao().observeGlobalFeedState().map { state ->
        GlobalFeedState(
            hasMore = state?.hasMore == true,
            isLoading = state?.syncState == "LOADING",
            lastUpdatedAt = state?.lastUpdatedAt,
            lastErrorCode = state?.lastErrorCode,
        )
    }

    val cloudDeletionState: Flow<CloudDeletionState> = database.cloudDao().observeCloudDeletionState().map { state ->
        when (state?.state) {
            null, "IDLE" -> CloudDeletionState.Idle
            "QUEUED" -> CloudDeletionState.Queued
            "PROCESSING", "UNPUBLISHED" -> CloudDeletionState.Processing
            "COMPLETED" -> CloudDeletionState.Completed(deletionContributionCount(state.affectedContributionIdsJson))
            else -> CloudDeletionState.Failed(state?.lastErrorCode)
        }
    }

    val syncOutboxEvents: Flow<List<SyncOutboxEvent>> = database.cloudDao().observeOutbox().map { events ->
        events.mapNotNull { event ->
            runCatching {
                SyncOutboxEvent(
                    id = event.id,
                    eventType = event.eventType,
                    version = event.version,
                    idempotencyKey = event.idempotencyKey,
                    attempts = event.attempts,
                    nextAttemptAt = event.nextAttemptAt,
                    state = SyncOutboxState.valueOf(event.state),
                    lastErrorCode = event.lastErrorCode,
                )
            }.getOrNull()
        }
    }

    val readNotificationKeys: Flow<Set<String>> = database.notificationDao().observeAll().map { states ->
        states.filter(NotificationStateEntity::isRead).mapTo(mutableSetOf(), NotificationStateEntity::notificationKey)
    }

    init {
        scope.launch { ensureCatalog() }
    }

    fun catalog(languageTag: String): Flow<List<Disease>> =
        database.catalogDao().observeCatalog().map { rows -> rows.map { it.toDomain(languageTag) } }

    suspend fun saveScan(result: ScanResult): ScanResult {
        ensureCatalog()
        val committedImage = snapshotStore?.commit(result.imagePath, result.id) ?: result.imagePath
        val committedResult = result.copy(imagePath = committedImage)
        val (session, detections) = ScanSessionMapper.fromDomain(committedResult)
        try {
            database.scanSessionDao().insertSessionWithDetections(session, detections)
        } catch (error: Throwable) {
            if (committedImage != result.imagePath) snapshotStore?.removeCommitted(committedImage)
            throw error
        }
        return committedResult
    }

    suspend fun stageSnapshot(frame: com.eggplant.detector.detection.api.RgbFrame): String? =
        kotlinx.coroutines.withContext(Dispatchers.IO) { snapshotStore?.stage(frame) }

    fun discardSnapshot(path: String?) = snapshotStore?.discard(path)

    fun removeOutboxPhoto(path: String?) = snapshotStore?.removeOutboxPhoto(path)

    fun shareEligibility(result: ScanResult?, sharingEnabled: Boolean): ShareEligibility = when {
        !sharingEnabled -> ShareEligibility.Ineligible(ShareEligibility.Reason.SHARING_DISABLED)
        result == null || result.outcome != com.eggplant.detector.domain.model.ScanOutcome.DISEASE -> ShareEligibility.Ineligible(ShareEligibility.Reason.UNSUPPORTED_RESULT)
        result.confidence < 50 -> ShareEligibility.Ineligible(ShareEligibility.Reason.LOW_CONFIDENCE)
        result.source == "gallery" -> ShareEligibility.Ineligible(ShareEligibility.Reason.GALLERY_SOURCE)
        result.source !in setOf("live", "capture") -> ShareEligibility.Ineligible(ShareEligibility.Reason.NOT_CONFIRMED)
        result.imagePath == null || !File(result.imagePath).isFile -> ShareEligibility.Ineligible(ShareEligibility.Reason.PHOTO_UNAVAILABLE)
        else -> ShareEligibility.Eligible
    }

    suspend fun enqueueGlobalShare(result: ScanResult, sharingEnabled: Boolean): ShareEligibility =
        sharingMutex.withLock {
            requireCloudConfigured()
            val eligibility = shareEligibility(result, sharingEnabled)
            if (eligibility !is ShareEligibility.Eligible) return@withLock eligibility
            val dao = database.cloudDao()
            val idempotencyKey = "global:${result.id}"
            val existing = dao.outboxByIdempotencyKey(idempotencyKey)
            if (existing != null && existing.version >= REVALIDATED_SHARE_EVENT_VERSION &&
                existing.state !in setOf("FAILED", "CANCELLED")
            ) {
                val now = Instant.now().toString()
                database.withTransaction { queueSharingConsent(enabled = true, now = now) }
                cloudSync?.invoke()
                return@withLock ShareEligibility.Eligible
            }

            val store = requireNotNull(snapshotStore) { "Cloud photo storage is unavailable." }
            val stagedPhoto = store.copyForOutbox(
                requireNotNull(result.imagePath),
                "global",
                "${result.id}-${UUID.randomUUID()}",
            )
            val revalidatedConfidence = try {
                requireNotNull(sharePhotoRevalidator) { "Share-photo validation is unavailable." }
                    .revalidate(stagedPhoto, result.diseaseId)
            } catch (error: Throwable) {
                store.removeOutboxPhoto(stagedPhoto)
                throw error
            }
            if (revalidatedConfidence == null) {
                store.removeOutboxPhoto(stagedPhoto)
                return@withLock ShareEligibility.Ineligible(ShareEligibility.Reason.NOT_CONFIRMED)
            }

            val now = Instant.now().toString()
            val payload = globalSharePayload(
                result.id,
                result.diseaseId,
                revalidatedConfidence,
                result.source,
                result.modelVersion,
                stagedPhoto,
            )
            try {
                database.withTransaction {
                    queueSharingConsent(enabled = true, now = now)
                    dao.upsertOutbox(
                        SyncOutboxEntity(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            eventType = "GLOBAL_SHARE",
                            version = REVALIDATED_SHARE_EVENT_VERSION,
                            idempotencyKey = idempotencyKey,
                            payloadJson = payload.toString(),
                            state = "PENDING",
                            attempts = 0,
                            nextAttemptAt = now,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            } catch (error: Throwable) {
                store.removeOutboxPhoto(stagedPhoto)
                throw error
            }
            existing?.photoPathOrNull()?.takeIf { it != stagedPhoto }?.let(store::removeOutboxPhoto)
            cloudSync?.invoke()
            ShareEligibility.Eligible
        }

    suspend fun setSharingConsent(enabled: Boolean) = sharingMutex.withLock {
        requireCloudConfigured()
        val dao = database.cloudDao()
        val pendingShares = if (enabled) emptyList() else dao.pendingShareEvents()
        val now = Instant.now().toString()
        database.withTransaction {
            if (!enabled) dao.cancelPendingShares(now)
            queueSharingConsent(enabled, now)
        }
        if (!enabled) {
            pendingShares.mapNotNull(SyncOutboxEntity::photoPathOrNull)
                .forEach { snapshotStore?.removeOutboxPhoto(it) }
        }
        cloudSync?.invoke()
    }

    suspend fun enqueueDiseaseRequest(
        requestedName: String?,
        notes: String?,
        photoPaths: List<String>,
        photoSources: List<String>,
        rightsConsent: Boolean,
    ): String {
        requireCloudConfigured()
        val normalizedName = requestedName?.trim()?.takeIf(String::isNotEmpty)
        val normalizedNotes = notes?.trim()?.takeIf(String::isNotEmpty)
        require(normalizedName == null || normalizedName.length in 2..120)
        require(normalizedNotes == null || normalizedNotes.length <= DISEASE_REQUEST_NOTES_MAX_LENGTH)
        require(photoPaths.size in 1..3)
        require(photoSources.size == photoPaths.size && photoSources.all { it in CAMERA_REQUEST_SOURCES }) {
            "Disease-request photos must come from the in-app camera."
        }
        require(rightsConsent)
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val store = requireNotNull(snapshotStore) { "Cloud photo storage is unavailable." }
        val staged = mutableListOf<String>()
        try {
            photoPaths.forEachIndexed { index, path ->
                staged += store.copyForOutbox(path, "request", id, index)
            }
            database.withTransaction {
                database.cloudDao().upsertDiseaseRequest(
                    DiseaseRequestEntity(id, id, normalizedName, normalizedNotes, "eggplant-yolo26m-v3-clean-768-20260707", rightsConsent, false, "QUEUED", 0f, null, now, now),
                )
                database.cloudDao().upsertDiseaseRequestPhotos(staged.mapIndexed { index, path ->
                    DiseaseRequestPhotoEntity(id, index, path, null, "QUEUED", "pending", File(path).length(), photoSources[index])
                })
                database.cloudDao().upsertOutbox(
                    SyncOutboxEntity(
                        id = UUID.randomUUID().toString(), eventType = "DISEASE_REQUEST", version = 1,
                        idempotencyKey = "request:$id", payloadJson = diseaseRequestPayload(id, normalizedName, normalizedNotes, "eggplant-yolo26m-v3-clean-768-20260707", staged, photoSources, rightsConsent, false).toString(),
                        state = "PENDING", attempts = 0, nextAttemptAt = now, createdAt = now, updatedAt = now,
                    ),
                )
            }
        } catch (error: Throwable) {
            staged.forEach { path -> runCatching { store.removeOutboxPhoto(path) } }
            throw error
        }
        cloudSync?.invoke()
        return id
    }

    suspend fun retryDiseaseRequest(clientRequestId: String): Boolean {
        requireCloudConfigured()
        val dao = database.cloudDao()
        val request = dao.diseaseRequestByClientId(clientRequestId) ?: return false
        val event = dao.outboxByIdempotencyKey("request:$clientRequestId") ?: return false
        if (event.state !in setOf("FAILED", "RETRY")) return false
        val photos = dao.requestPhotos(request.id)
        if (photos.isEmpty() || photos.any { !File(it.localPhotoPath).isFile }) return false
        val now = Instant.now().toString()
        database.withTransaction {
            dao.upsertOutbox(
                event.copy(
                    state = "PENDING",
                    attempts = 0,
                    nextAttemptAt = now,
                    lastErrorCode = null,
                    updatedAt = now,
                ),
            )
            dao.updateDiseaseRequestState(clientRequestId, "QUEUED", 0f, now)
            dao.updateDiseaseRequestPhotoState(request.id, "QUEUED")
        }
        cloudSync?.invoke()
        return true
    }

    suspend fun cancelDiseaseRequest(clientRequestId: String): Boolean {
        val dao = database.cloudDao()
        val request = dao.diseaseRequestByClientId(clientRequestId) ?: return false
        val event = dao.outboxByIdempotencyKey("request:$clientRequestId") ?: return false
        // Once upload has started, the server may already own one or more
        // private objects. Finish that atomic submission rather than showing a
        // local cancellation that cannot be guaranteed remotely.
        if (event.state !in setOf("PENDING", "RETRY")) return false
        val photos = dao.requestPhotos(request.id)
        val now = Instant.now().toString()
        database.withTransaction {
            dao.upsertOutbox(event.copy(state = "CANCELLED", updatedAt = now))
            dao.updateDiseaseRequestState(clientRequestId, "CANCELLED", request.uploadProgress, now)
            dao.updateDiseaseRequestPhotoState(request.id, "CANCELLED")
        }
        photos.forEach { photo -> snapshotStore?.removeOutboxPhoto(photo.localPhotoPath) }
        return true
    }

    fun discardDiseaseRequestDraftPhotos(paths: Collection<String>) {
        paths.forEach { snapshotStore?.discard(it) }
    }

    suspend fun cancelPendingShares() = sharingMutex.withLock {
        val pending = database.cloudDao().pendingShareEvents()
        database.cloudDao().cancelPendingShares(Instant.now().toString())
        pending.mapNotNull(SyncOutboxEntity::photoPathOrNull)
            .forEach { snapshotStore?.removeOutboxPhoto(it) }
    }

    fun refreshCloud() = cloudSync?.invoke()

    fun loadMoreGlobalScans() = cloudSyncLoadMore?.invoke()

    val isCloudConfigured: Boolean get() = cloudConfigured?.invoke() == true

    suspend fun retryOutboxEvent(eventId: String): Boolean {
        val dao = database.cloudDao()
        val event = dao.outboxById(eventId) ?: return false
        if (event.state !in setOf("FAILED", "RETRY")) return false
        val now = Instant.now().toString()
        dao.upsertOutbox(
            event.copy(
                state = "PENDING",
                attempts = 0,
                nextAttemptAt = now,
                lastErrorCode = null,
                updatedAt = now,
            ),
        )
        cloudSync?.invoke()
        return true
    }

    suspend fun enqueueContentReport(scanId: String, reason: String, details: String? = null) {
        requireCloudConfigured()
        require(reason in setOf("incorrect_result", "not_eggplant", "inappropriate", "duplicate", "other"))
        val now = Instant.now().toString()
        val idempotencyKey = "report:$scanId"
        if (database.cloudDao().outboxByIdempotencyKey(idempotencyKey) != null) return
        val payload = buildJsonObject {
            put("path", "/api/mobile/v1/global-scans/$scanId/reports")
            put("body", buildJsonObject { put("reason", reason); details?.let { put("details", it.take(1000)) } })
        }
        database.cloudDao().upsertOutbox(
            SyncOutboxEntity(UUID.randomUUID().toString(), "CONTENT_REPORT", 1, idempotencyKey, payload.toString(), "PENDING", 0, now, null, now, now),
        )
        cloudSync?.invoke()
    }

    suspend fun enqueueCloudDeletion() {
        requireCloudConfigured()
        val now = Instant.now().toString()
        cancelPendingShares()
        val existing = database.cloudDao().outboxByIdempotencyKey("deletion-request")
        if (existing != null && existing.state in setOf("PENDING", "RETRY", "UPLOADING")) return
        database.withTransaction {
            database.cloudDao().upsertOutbox(
                SyncOutboxEntity(
                    existing?.id ?: UUID.randomUUID().toString(),
                    "DELETION_REQUEST",
                    1,
                    "deletion-request",
                    "{}",
                    "PENDING",
                    0,
                    now,
                    null,
                    existing?.createdAt ?: now,
                    now,
                ),
            )
            database.cloudDao().upsertCloudDeletionState(
                com.eggplant.detector.data.database.entity.CloudDeletionStateEntity(
                    state = "QUEUED",
                    affectedContributionIdsJson = "[]",
                    lastErrorCode = null,
                    updatedAt = now,
                ),
            )
        }
        cloudSync?.invoke()
    }

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
                seed.references,
            )
            catalogSeeded = true
        }
    }

    private suspend fun queueSharingConsent(enabled: Boolean, now: String) {
        val dao = database.cloudDao()
        val existing = dao.outboxByIdempotencyKey(SHARING_CONSENT_IDEMPOTENCY_KEY)
        val payload = sharingConsentPayload(enabled).toString()
        if (existing?.payloadJson == payload && existing.state !in setOf("FAILED", "CANCELLED")) return
        dao.upsertOutbox(
            SyncOutboxEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                eventType = "SHARING_CONSENT",
                version = 1,
                idempotencyKey = SHARING_CONSENT_IDEMPOTENCY_KEY,
                payloadJson = payload,
                state = "PENDING",
                attempts = 0,
                nextAttemptAt = now,
                lastErrorCode = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun requireCloudConfigured() {
        check(isCloudConfigured) { "Cloud is unavailable in this build." }
    }
}

private val cacheJson = Json { ignoreUnknownKeys = true }

private fun SyncOutboxEntity.photoPathOrNull(): String? = runCatching {
    cacheJson.parseToJsonElement(payloadJson).jsonObject["photoPath"]?.jsonPrimitive?.content
}.getOrNull()

private const val SHARING_CONSENT_IDEMPOTENCY_KEY = "sharing-consent"
private const val REVALIDATED_SHARE_EVENT_VERSION = 2
private const val DISEASE_REQUEST_NOTES_MAX_LENGTH = 200
private val CAMERA_REQUEST_SOURCES = setOf("live", "capture")

private fun deletionContributionCount(idsJson: String): Int = runCatching {
    cacheJson.parseToJsonElement(idsJson).jsonArray.size
}.getOrDefault(0)

private fun com.eggplant.detector.data.database.entity.GlobalScanCacheEntity.toDomain(catalog: List<Disease>): GlobalScan? = runCatching {
    val payload = cacheJson.parseToJsonElement(contentJson).jsonObject
    val disease = catalog.firstOrNull { it.id == diseaseId }
    val content = payload["content"]?.jsonObject
    GlobalScan(
        id = id,
        diseaseId = diseaseId,
        diseaseName = content?.get("name")?.jsonPrimitive?.content ?: disease?.name ?: diseaseId,
        confidence = (confidence * 100).toInt().coerceIn(0, 100),
        photoPath = cachedPhotoPath,
        publishedAt = publishedAt,
        symptoms = payload["signs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: disease?.signs.orEmpty(),
        causes = content?.get("causes")?.jsonPrimitive?.content ?: disease?.causes.orEmpty(),
        prevention = content?.get("prevention")?.jsonPrimitive?.content ?: disease?.prevention.orEmpty(),
        guidance = content?.get("guidance")?.jsonPrimitive?.content ?: disease?.guidance.orEmpty(),
        whenToAct = content?.get("when_to_act")?.jsonPrimitive?.content ?: disease?.whenToAct.orEmpty(),
        disclaimer = content?.get("disclaimer")?.jsonPrimitive?.content ?: disease?.disclaimer.orEmpty(),
        references = payload["references"]?.jsonArray?.map { element ->
            val reference = element.jsonObject
            DiseaseReference(reference.getValue("publisher").jsonPrimitive.content, reference.getValue("title").jsonPrimitive.content, reference.getValue("url").jsonPrimitive.content)
        } ?: disease?.references.orEmpty(),
    )
}.getOrNull()

private fun DiseaseCatalogBundle.toDomain(languageTag: String): Disease {
    val normalizedLanguage = if (languageTag in setOf("fil", "tl")) "fil" else "en"
    val localization = localizations.firstOrNull { it.languageTag == normalizedLanguage }
        ?: localizations.first { it.languageTag == "en" }
    val localizedSigns = signs.filter { it.languageTag == localization.languageTag }.sortedBy { it.position }
    val treatment = treatments.firstOrNull { it.languageTag == localization.languageTag }
    val localizedReferences = references.filter { it.languageTag == localization.languageTag }.sortedBy { it.position }
    return Disease(
        id = disease.id,
        name = localization.name,
        type = DiseaseType.valueOf(disease.category),
        symptomPreview = localization.symptomPreview,
        signs = localizedSigns.map { it.text },
        treatment = treatment?.procedures.orEmpty(),
        prevention = localization.prevention,
        causes = localization.causes,
        guidance = localization.guidance,
        whenToAct = localization.whenToAct,
        disclaimer = localization.disclaimer,
        references = localizedReferences.map {
            com.eggplant.detector.domain.model.DiseaseReference(it.publisher, it.title, it.url)
        },
    )
}
