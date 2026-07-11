package com.eggplant.detector.data.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.room.withTransaction
import com.eggplant.detector.app.EggplantApplication
import com.eggplant.detector.data.database.entity.GlobalRankingCacheEntity
import com.eggplant.detector.data.database.entity.GlobalScanCacheEntity
import com.eggplant.detector.data.database.entity.SyncOutboxEntity
import com.eggplant.detector.data.database.entity.AppSettingsEntity
import com.eggplant.detector.data.database.entity.DiseaseEntity
import com.eggplant.detector.data.database.entity.DiseaseLocalizationEntity
import com.eggplant.detector.data.database.entity.DiseaseReferenceEntity
import com.eggplant.detector.data.database.entity.DiseaseRequestEntity
import com.eggplant.detector.data.database.entity.CloudDeletionStateEntity
import com.eggplant.detector.data.database.entity.GlobalFeedStateEntity
import com.eggplant.detector.data.database.entity.DiseaseSignEntity
import com.eggplant.detector.data.database.entity.TreatmentEntity
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.domain.model.DiseaseType
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CloudSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val application = context.applicationContext as EggplantApplication
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val client = application.cloudApiClient
        if (!client.isConfigured) return Result.success()
        val dao = application.database.cloudDao()
        val events = dao.pendingEvents(Instant.now().toString())
        val loadMoreGlobalFeed = inputData.getBoolean(INPUT_LOAD_MORE_GLOBAL_FEED, false)
        var retryNeeded = false
        eventLoop@ for (event in events) {
            if (!isCurrentEvent(event, "PENDING", "RETRY")) continue
            if (event.eventType == "GLOBAL_SHARE") {
                when (sharingConsentSyncState()) {
                    ConsentSyncState.SYNCED -> Unit
                    ConsentSyncState.PENDING -> {
                        val now = Instant.now()
                        dao.upsertOutbox(
                            event.copy(
                                state = "RETRY",
                                nextAttemptAt = now.plusSeconds(30).toString(),
                                updatedAt = now.toString(),
                            ),
                        )
                        retryNeeded = true
                        continue@eventLoop
                    }
                    ConsentSyncState.DISABLED -> {
                        dao.upsertOutbox(event.copy(state = "CANCELLED", updatedAt = Instant.now().toString()))
                        removePayloadPhotos(event.payloadJson)
                        continue@eventLoop
                    }
                    ConsentSyncState.FAILED -> {
                        dao.upsertOutbox(
                            event.copy(
                                state = "FAILED",
                                lastErrorCode = "sharing_consent_failed",
                                updatedAt = Instant.now().toString(),
                            ),
                        )
                        continue@eventLoop
                    }
                }
            }
            val uploading = event.copy(state = "UPLOADING", attempts = event.attempts + 1, updatedAt = Instant.now().toString())
            dao.upsertOutbox(uploading)
            if (event.eventType == "DISEASE_REQUEST") {
                updateDiseaseRequestForEvent(event, "UPLOADING", 0.05f)
            }
            try {
                val payload = json.parseToJsonElement(event.payloadJson).jsonObject
                val operationCompleted = when (event.eventType) {
                    "SHARING_CONSENT" -> {
                        client.post("/api/mobile/v1/me/sharing-consent", payload)
                        true
                    }
                    "GLOBAL_SHARE" -> uploadGlobalShare(client, payload, uploading)
                    "DISEASE_REQUEST" -> {
                        val status = uploadDiseaseRequest(client, payload, uploading) { progress ->
                            updateDiseaseRequestForEvent(event, "UPLOADING", progress)
                        }
                        if (status != null && isCurrentEvent(uploading, "UPLOADING")) {
                            val clientRequestId = payload.getValue("clientRequestId").jsonPrimitive.content
                            dao.updateDiseaseRequestState(clientRequestId, status.uppercase(Locale.ROOT), 1f, Instant.now().toString())
                            dao.updateDiseaseRequestPhotoState(clientRequestId, "UPLOADED")
                            true
                        } else {
                            false
                        }
                    }
                    "CONTENT_REPORT" -> {
                        client.post(payload.getValue("path").jsonPrimitive.content, payload.getValue("body").jsonObject)
                        true
                    }
                    "DELETION_REQUEST" -> {
                        val deletion = client.post("/api/mobile/v1/me/deletion-request", buildJsonObject {})
                        updateDeletionState(deletion)
                        true
                    }
                    else -> error("Unsupported outbox event ${event.eventType}.")
                }
                if (!operationCompleted || !isCurrentEvent(uploading, "UPLOADING")) continue@eventLoop
                dao.upsertOutbox(uploading.copy(state = "COMPLETED", updatedAt = Instant.now().toString(), lastErrorCode = null))
                removePayloadPhotos(event.payloadJson)
            } catch (error: CloudApiException) {
                if (!isCurrentEvent(uploading, "UPLOADING")) continue@eventLoop
                val retryable = error.status == 408 || error.status == 429 || error.status >= 500
                val willRetry = retryable && uploading.attempts < MAX_EVENT_ATTEMPTS
                retryNeeded = retryNeeded || willRetry
                val delaySeconds = (30L shl event.attempts.coerceAtMost(6)).coerceAtMost(3600L)
                dao.upsertOutbox(uploading.copy(
                    state = if (willRetry) "RETRY" else "FAILED",
                    nextAttemptAt = Instant.now().plusSeconds(delaySeconds).toString(),
                    lastErrorCode = error.code,
                    updatedAt = Instant.now().toString(),
                ))
                updateDiseaseRequestForEvent(event, if (willRetry) "RETRY" else "FAILED", 0f)
                updateDeletionFailure(event, error.code, willRetry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                if (!isCurrentEvent(uploading, "UPLOADING")) continue@eventLoop
                val willRetry = uploading.attempts < MAX_EVENT_ATTEMPTS
                retryNeeded = retryNeeded || willRetry
                dao.upsertOutbox(uploading.copy(
                    state = if (willRetry) "RETRY" else "FAILED",
                    nextAttemptAt = Instant.now().plusSeconds(60).toString(),
                    lastErrorCode = "network_error",
                    updatedAt = Instant.now().toString(),
                ))
                updateDiseaseRequestForEvent(event, if (willRetry) "RETRY" else "FAILED", 0f)
                updateDeletionFailure(event, "network_error", willRetry)
            } catch (_: Exception) {
                if (!isCurrentEvent(uploading, "UPLOADING")) continue@eventLoop
                dao.upsertOutbox(uploading.copy(
                    state = "FAILED",
                    lastErrorCode = "invalid_event",
                    updatedAt = Instant.now().toString(),
                ))
                updateDiseaseRequestForEvent(event, "FAILED", 0f)
                updateDeletionFailure(event, "invalid_event", false)
            }
        }
        val languageTag = normalizedLanguage(application.database.settingsDao().current()?.languageTag)
        retryNeeded = refreshSafely(retryNeeded) { refreshCatalog(client, languageTag) }
        retryNeeded = refreshSafely(retryNeeded) { refreshDiseaseRequests(client) }
        retryNeeded = refreshSafely(retryNeeded) { refreshGlobalFeed(client, languageTag, loadMoreGlobalFeed) }
        retryNeeded = refreshSafely(retryNeeded) { refreshDeletionStatus(client) }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    private suspend fun uploadGlobalShare(
        client: CloudApiClient,
        payload: JsonObject,
        event: SyncOutboxEntity,
    ): Boolean {
        val photo = File(payload.getValue("photoPath").jsonPrimitive.content)
        check(photo.isFile && photo.length() in 1..8_388_608) { "Share photo is unavailable or too large." }
        if (!isCurrentEvent(event, "UPLOADING")) return false
        val sha256 = photo.sha256()
        val metadata = buildJsonObject {
            listOf("clientScanId", "diseaseId", "confidence", "source", "modelVersion").forEach { key -> put(key, payload.getValue(key)) }
            put("contentLength", photo.length())
            put("sha256", sha256)
        }
        val intent = client.post("/api/mobile/v1/global-shares/intents", metadata)
        if (intent["alreadyPublished"]?.jsonPrimitive?.content == "true") return true
        val photoBytes = withContext(Dispatchers.IO) { photo.readBytes() }
        if (!isCurrentEvent(event, "UPLOADING")) return false
        client.upload(intent.getValue("signedUrl").jsonPrimitive.content, photoBytes)
        if (!isCurrentEvent(event, "UPLOADING")) return false
        client.post("/api/mobile/v1/global-shares/complete", buildJsonObject {
            listOf("clientScanId", "diseaseId", "confidence", "source", "modelVersion").forEach { key -> put(key, payload.getValue(key)) }
            put("path", intent.getValue("path"))
        })
        return true
    }

    private suspend fun uploadDiseaseRequest(
        client: CloudApiClient,
        payload: JsonObject,
        event: SyncOutboxEntity,
        onProgress: suspend (Float) -> Unit,
    ): String? {
        val photos = payload.photoPaths().map(::File)
        val sources = payload.photoSources()
        check(photos.size in 1..3 && photos.all { it.isFile && it.length() in 1..8_388_608 }) { "Disease-request photos are unavailable or too large." }
        check(sources.size == photos.size && sources.all { it in CAMERA_REQUEST_SOURCES }) { "Disease-request photos must be captured in-app." }
        if (!isCurrentEvent(event, "UPLOADING")) return null
        val response = client.post("/api/mobile/v1/disease-requests", buildJsonObject {
            listOf("clientRequestId", "modelVersion", "rightsConsent", "trainingConsent").forEach { key -> put(key, payload.getValue(key)) }
            payload["requestedName"]?.let { put("requestedName", it) }
            payload["notes"]?.let { put("notes", it) }
            put("photos", buildJsonArray {
                photos.forEachIndexed { index, photo ->
                    add(buildJsonObject {
                        put("contentLength", photo.length())
                        put("sha256", photo.sha256())
                        put("source", sources[index])
                    })
                }
            })
        })
        val status = response.getValue("status").jsonPrimitive.content
        val uploads = response.getValue("uploads").jsonArray
        if (!isCurrentEvent(event, "UPLOADING")) return null
        onProgress(0.15f)
        if (status != "upload_pending") {
            check(uploads.isEmpty()) { "A terminal disease request unexpectedly included uploads." }
            return status
        }
        check(uploads.size == photos.size) { "Disease-request upload metadata is incomplete." }
        photos.forEachIndexed { index, photo ->
            if (!isCurrentEvent(event, "UPLOADING")) return null
            client.upload(
                uploads[index].jsonObject.getValue("signedUrl").jsonPrimitive.content,
                withContext(Dispatchers.IO) { photo.readBytes() },
            )
            if (!isCurrentEvent(event, "UPLOADING")) return null
            onProgress(0.15f + (0.70f * (index + 1) / photos.size))
        }
        if (!isCurrentEvent(event, "UPLOADING")) return null
        val completed = client.post("/api/mobile/v1/disease-requests/${response.getValue("id").jsonPrimitive.content}/complete", buildJsonObject {})
        if (!isCurrentEvent(event, "UPLOADING")) return null
        onProgress(0.95f)
        return completed.getValue("status").jsonPrimitive.content
    }

    private suspend fun updateDiseaseRequestForEvent(event: SyncOutboxEntity, state: String, progress: Float) {
        if (event.eventType != "DISEASE_REQUEST") return
        val clientRequestId = runCatching {
            json.parseToJsonElement(event.payloadJson).jsonObject.getValue("clientRequestId").jsonPrimitive.content
        }.getOrNull() ?: return
        application.database.cloudDao().updateDiseaseRequestState(
            clientRequestId,
            state,
            progress.coerceIn(0f, 1f),
            Instant.now().toString(),
        )
    }

    private suspend fun updateDeletionFailure(event: SyncOutboxEntity, errorCode: String, willRetry: Boolean) {
        if (event.eventType != "DELETION_REQUEST") return
        val dao = application.database.cloudDao()
        val existing = dao.cloudDeletionState()
        dao.upsertCloudDeletionState(
            CloudDeletionStateEntity(
                state = if (willRetry) "QUEUED" else "FAILED",
                affectedContributionIdsJson = existing?.affectedContributionIdsJson ?: "[]",
                lastErrorCode = errorCode,
                updatedAt = Instant.now().toString(),
            ),
        )
    }

    private suspend fun refreshSafely(existingRetry: Boolean, block: suspend () -> Unit): Boolean = try {
        block()
        existingRetry
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        existingRetry || runAttemptCount + 1 < MAX_WORKER_ATTEMPTS
    }

    private suspend fun isCurrentEvent(event: SyncOutboxEntity, vararg allowedStates: String): Boolean {
        val current = application.database.cloudDao().outboxById(event.id) ?: return false
        return current.payloadJson == event.payloadJson && current.state in allowedStates
    }

    private suspend fun sharingConsentSyncState(): ConsentSyncState {
        val consent = application.database.cloudDao().outboxByIdempotencyKey(SHARING_CONSENT_KEY)
            ?: return ConsentSyncState.FAILED
        val enabled = runCatching {
            json.parseToJsonElement(consent.payloadJson).jsonObject.getValue("enabled").jsonPrimitive.content == "true"
        }.getOrDefault(false)
        if (!enabled) return ConsentSyncState.DISABLED
        return when (consent.state) {
            "COMPLETED" -> ConsentSyncState.SYNCED
            "PENDING", "RETRY", "UPLOADING" -> ConsentSyncState.PENDING
            else -> ConsentSyncState.FAILED
        }
    }

    private fun removePayloadPhotos(payloadJson: String) {
        val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }.getOrNull() ?: return
        payload["photoPath"]?.jsonPrimitive?.content?.let(application.repository::removeOutboxPhoto)
        payload["photoPaths"]?.jsonArray?.forEach { application.repository.removeOutboxPhoto(it.jsonPrimitive.content) }
    }

    private suspend fun refreshCatalog(client: CloudApiClient, languageTag: String) {
        val settingsDao = application.database.settingsDao()
        val previous = settingsDao.current() ?: AppSettingsEntity(languageTag = languageTag)
        val response = client.getConditional("/api/mobile/v1/catalog?lang=$languageTag", previous.contentEtag)
        val now = Instant.now().toString()
        if (response.status == 304) {
            val latest = settingsDao.current() ?: previous
            settingsDao.upsert(latest.copy(lastGlobalSyncAt = now))
            return
        }
        val body = requireNotNull(response.body) { "Catalog response is empty." }
        val responseLanguage = body.getValue("languageTag").jsonPrimitive.content
        check(responseLanguage == languageTag) { "Catalog language does not match the request." }
        val version = body.getValue("version").jsonPrimitive.content.toInt()
        check(version > 0) { "Catalog version is invalid." }
        val expectedClasses = ModelMetadata.EGGPLANT_YOLO26M.classes
            .filterNot { it.isHealthy }
            .associateBy { requireNotNull(it.diseaseId) }
        val rows = body.getValue("diseases").jsonArray.map { element -> element.jsonObject }
        check(rows.size == expectedClasses.size && rows.map { it.getValue("id").jsonPrimitive.content }.toSet() == expectedClasses.keys) {
            "Catalog model mappings are incomplete."
        }
        val diseases = mutableListOf<DiseaseEntity>()
        val localizations = mutableListOf<DiseaseLocalizationEntity>()
        val signs = mutableListOf<DiseaseSignEntity>()
        val treatments = mutableListOf<TreatmentEntity>()
        val references = mutableListOf<DiseaseReferenceEntity>()
        rows.forEach { row ->
            val id = row.getValue("id").jsonPrimitive.content
            val expected = requireNotNull(expectedClasses[id])
            val modelClassIndex = row.getValue("model_class_index").jsonPrimitive.content.toInt()
            val modelLabel = row.getValue("model_label").jsonPrimitive.content
            check(modelClassIndex == expected.index && modelLabel == expected.modelLabel) {
                "Catalog model mapping changed for $id."
            }
            val category = row.getValue("category").jsonPrimitive.content
            check(DiseaseType.entries.any { it.name == category }) { "Catalog category is invalid." }
            val artworkKey = row.getValue("artwork_key").jsonPrimitive.content
            check(artworkKey.isNotBlank()) { "Catalog artwork key is missing." }
            val content = row.getValue("content").jsonObject
            diseases += DiseaseEntity(id, modelClassIndex, modelLabel, category, artworkKey)
            localizations += DiseaseLocalizationEntity(
                diseaseId = id,
                languageTag = languageTag,
                name = content.requiredText("name"),
                description = content.requiredText("description"),
                symptomPreview = content.requiredText("symptom_preview"),
                prevention = content.requiredText("prevention"),
                causes = content.requiredText("causes"),
                guidance = content.requiredText("guidance"),
                whenToAct = content.requiredText("when_to_act"),
                disclaimer = content.requiredText("disclaimer"),
            )
            treatments += TreatmentEntity(
                diseaseId = id,
                languageTag = languageTag,
                title = if (languageTag == "fil") "Paggamot" else "Recommended action",
                treatmentType = "RECOMMENDED_ACTION",
                procedures = content.requiredText("recommended_action"),
            )
            row.getValue("signs").jsonArray.forEachIndexed { index, sign ->
                val text = sign.jsonPrimitive.content.trim()
                check(text.isNotEmpty()) { "Catalog sign is empty." }
                signs += DiseaseSignEntity(id, languageTag, index, text)
            }
            row.getValue("references").jsonArray.forEach { referenceElement ->
                val reference = referenceElement.jsonObject
                val position = reference.getValue("position").jsonPrimitive.content.toInt()
                val url = reference.requiredText("url")
                check(url.startsWith("https://")) { "Catalog reference URL is not secure." }
                references += DiseaseReferenceEntity(
                    diseaseId = id,
                    languageTag = languageTag,
                    position = position,
                    publisher = reference.requiredText("publisher"),
                    title = reference.requiredText("title"),
                    url = url,
                )
            }
        }
        check(diseases.map(DiseaseEntity::modelClassIndex).distinct().size == diseases.size)
        application.database.withTransaction {
            application.database.catalogDao().replaceLocalizedContent(
                languageTag,
                diseases,
                localizations,
                signs,
                treatments,
                references,
            )
            val latest = settingsDao.current() ?: previous
            settingsDao.upsert(
                latest.copy(
                    contentSyncVersion = version,
                    contentEtag = response.etag,
                    lastGlobalSyncAt = now,
                ),
            )
        }
    }

    private suspend fun refreshDiseaseRequests(client: CloudApiClient) {
        val response = client.get("/api/mobile/v1/me/disease-requests")
        val rows = response.getValue("items").jsonArray.map { it.jsonObject }
        check(rows.size <= 100) { "Disease-request status response is too large." }
        val dao = application.database.cloudDao()
        application.database.withTransaction {
            rows.forEach { row ->
                val remoteId = UUID.fromString(row.getValue("id").jsonPrimitive.content).toString()
                val clientRequestId = UUID.fromString(row.getValue("client_request_id").jsonPrimitive.content).toString()
                val remoteStatus = row.getValue("status").jsonPrimitive.content
                check(remoteStatus in REMOTE_REQUEST_STATES) { "Disease-request status is invalid." }
                val existing = dao.diseaseRequestByClientId(clientRequestId)
                val preserveLocalState = remoteStatus == "upload_pending" && existing?.state in LOCAL_REQUEST_ACTIVE_STATES
                dao.upsertDiseaseRequest(
                    DiseaseRequestEntity(
                        id = existing?.id ?: remoteId,
                        clientRequestId = clientRequestId,
                        requestedName = row.optionalText("requested_name")?.take(120),
                        // Legacy server rows may predate the 200-character
                        // limit. They remain readable and the UI clamps them.
                        notes = row.optionalText("notes")?.take(MAXIMUM_LEGACY_NOTE_LENGTH),
                        modelVersion = existing?.modelVersion ?: ModelMetadata.EGGPLANT_YOLO26M.modelVersion,
                        rightsConsent = existing?.rightsConsent ?: true,
                        trainingConsent = existing?.trainingConsent ?: false,
                        state = if (preserveLocalState) requireNotNull(existing).state else remoteStatus.uppercase(Locale.ROOT),
                        uploadProgress = if (preserveLocalState) requireNotNull(existing).uploadProgress else if (remoteStatus == "upload_pending") 0f else 1f,
                        adminNote = row.optionalText("admin_note"),
                        createdAt = existing?.createdAt ?: row.getValue("created_at").jsonPrimitive.content,
                        updatedAt = row.getValue("updated_at").jsonPrimitive.content,
                    ),
                )
            }
        }
    }

    private suspend fun refreshGlobalFeed(client: CloudApiClient, languageTag: String, loadMore: Boolean) {
        val dao = application.database.cloudDao()
        val previousState = dao.globalFeedState() ?: GlobalFeedStateEntity()
        val cursor = previousState.nextCursor?.takeIf { loadMore }
        if (loadMore && cursor == null) return
        dao.upsertGlobalFeedState(
            previousState.copy(syncState = "LOADING", lastErrorCode = null),
        )
        val query = buildString {
            append("/api/mobile/v1/global-scans?limit=30&lang=")
            append(languageTag)
            cursor?.let {
                append("&cursor=")
                append(URLEncoder.encode(it, Charsets.UTF_8.name()))
            }
        }
        val response = try {
            client.get(query)
        } catch (error: Throwable) {
            dao.upsertGlobalFeedState(
                previousState.copy(
                    syncState = "FAILED",
                    lastErrorCode = errorCode(error),
                    lastUpdatedAt = previousState.lastUpdatedAt,
                ),
            )
            throw error
        }
        val now = Instant.now().toString()
        val cacheRoot = File(applicationContext.cacheDir, "global-scans").apply { mkdirs() }
        val scans = response.getValue("items").jsonArray.map { element ->
            val item = element.jsonObject
            val id = UUID.fromString(item.getValue("id").jsonPrimitive.content).toString()
            val photoPath = item.optionalText("photoUrl")?.let { url ->
                val destination = File(cacheRoot, "$id.jpg")
                val previous = destination.takeIf(SafeJpeg::validate)?.absolutePath
                runCatching {
                    val temporary = File(cacheRoot, "$id.tmp")
                    client.download(url, temporary, MAXIMUM_JPEG_BYTES)
                    check(SafeJpeg.validate(temporary)) { "Community photo is not a safe JPEG." }
                    moveReplacing(temporary, destination)
                    destination.absolutePath
                }.getOrElse { previous }
            }
            GlobalScanCacheEntity(
                id = id,
                diseaseId = item.getValue("disease_id").jsonPrimitive.content,
                confidence = item.getValue("confidence").jsonPrimitive.content.toFloat(),
                source = item.getValue("source").jsonPrimitive.content,
                modelVersion = item.getValue("model_version").jsonPrimitive.content,
                cachedPhotoPath = photoPath,
                publishedAt = item.getValue("published_at").jsonPrimitive.content,
                expiresAt = item["expires_at"]?.jsonPrimitive?.content ?: Instant.now().plusSeconds(15_552_000).toString(),
                contentJson = item.toString(),
            )
        }
        val rankings = response.getValue("rankings").jsonArray.map { ranking ->
            val row = ranking.jsonObject
            GlobalRankingCacheEntity(row.getValue("diseaseId").jsonPrimitive.content, row.getValue("count").jsonPrimitive.content.toLong(), now)
        }
        val nextCursor = response.optionalText("nextCursor")
        application.database.withTransaction {
            if (!loadMore) {
                dao.clearGlobalScans()
                dao.clearGlobalRankings()
            }
            dao.upsertGlobalScans(scans)
            dao.upsertGlobalRankings(rankings)
            dao.upsertGlobalFeedState(
                previousState.copy(
                    nextCursor = nextCursor,
                    hasMore = !nextCursor.isNullOrBlank(),
                    syncState = "READY",
                    lastErrorCode = null,
                    lastUpdatedAt = now,
                ),
            )
            val settings = application.database.settingsDao().current() ?: AppSettingsEntity(languageTag = languageTag)
            application.database.settingsDao().upsert(settings.copy(lastGlobalSyncAt = now))
        }
        if (!loadMore) {
            val retained = scans.mapNotNull { it.cachedPhotoPath?.let(::File)?.name }.toSet()
            cacheRoot.listFiles().orEmpty().forEach { file ->
                if ((file.extension == "jpg" && file.name !in retained) || file.extension == "tmp") file.delete()
            }
        }
    }

    private suspend fun refreshDeletionStatus(client: CloudApiClient) {
        // Do not poll an endpoint until the user has actually requested this
        // operation; a brand-new anonymous identity has nothing to reconcile.
        val state = application.database.cloudDao().cloudDeletionState() ?: return
        if (state.state == "IDLE") return
        val response = client.get("/api/mobile/v1/me/deletion-request")
        updateDeletionState(response)
    }

    private suspend fun updateDeletionState(response: JsonObject) {
        val status = response.optionalText("status")?.lowercase(Locale.ROOT) ?: "processing"
        val state = when (status) {
            "completed" -> "COMPLETED"
            "failed" -> "FAILED"
            "queued", "pending" -> "QUEUED"
            else -> "PROCESSING"
        }
        val returnedIds = response["affectedContributionIds"]?.jsonArray
            ?.mapNotNull { runCatching { UUID.fromString(it.jsonPrimitive.content).toString() }.getOrNull() }
            .orEmpty()
        val existingIds = application.database.cloudDao().cloudDeletionState()
            ?.affectedContributionIdsJson
            ?.let { encoded -> runCatching { json.parseToJsonElement(encoded).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList()) }
            .orEmpty()
        // The POST response names newly unpublished contributions. Later
        // status polls intentionally omit that large list, so retain it until
        // terminal cache invalidation is complete.
        val affectedIds = if (returnedIds.isNotEmpty()) returnedIds else existingIds
        val now = Instant.now().toString()
        var removedPhotoPaths: List<String> = emptyList()
        application.database.withTransaction {
            application.database.cloudDao().upsertCloudDeletionState(
                CloudDeletionStateEntity(
                    state = state,
                    affectedContributionIdsJson = buildJsonArray {
                        affectedIds.forEach { id -> add(kotlinx.serialization.json.JsonPrimitive(id)) }
                    }.toString(),
                    lastErrorCode = response.optionalText("lastErrorCode"),
                    updatedAt = now,
                ),
            )
            if (state == "COMPLETED" && affectedIds.isNotEmpty()) {
                val rows = application.database.cloudDao().globalScansByIds(affectedIds)
                application.database.cloudDao().deleteGlobalScans(affectedIds)
                removedPhotoPaths = rows.mapNotNull(GlobalScanCacheEntity::cachedPhotoPath)
            }
        }
        removedPhotoPaths.forEach { path -> runCatching { File(path).delete() } }
    }
}

object CloudSyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "eggplant-cloud-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build(),
        )
    }

    fun refresh(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "eggplant-cloud-sync-now",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<CloudSyncWorker>().setConstraints(constraints).build(),
        )
    }

    fun loadMoreGlobalScans(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "eggplant-cloud-sync-more",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(INPUT_LOAD_MORE_GLOBAL_FEED to true))
                .build(),
        )
    }
}

private const val MAX_EVENT_ATTEMPTS = 8
private const val MAX_WORKER_ATTEMPTS = 8
private const val SHARING_CONSENT_KEY = "sharing-consent"
private const val INPUT_LOAD_MORE_GLOBAL_FEED = "load_more_global_feed"
private const val MAXIMUM_LEGACY_NOTE_LENGTH = 20_000
private val CAMERA_REQUEST_SOURCES = setOf("live", "capture")

private val REMOTE_REQUEST_STATES = setOf(
    "upload_pending",
    "submitted",
    "under_review",
    "planned",
    "needs_information",
    "not_supported",
    "closed",
)
private val LOCAL_REQUEST_ACTIVE_STATES = setOf("QUEUED", "UPLOADING", "RETRY", "FAILED", "CANCELLED")

private enum class ConsentSyncState { SYNCED, PENDING, DISABLED, FAILED }

private fun normalizedLanguage(languageTag: String?): String = if (languageTag in setOf("fil", "tl")) "fil" else "en"

private fun JsonObject.requiredText(key: String): String {
    val value = optionalText(key)
    check(!value.isNullOrBlank() && value.length <= 20_000) { "Cloud field $key is invalid." }
    return value
}

private fun JsonObject.optionalText(key: String): String? =
    runCatching { get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }.getOrNull()

private fun errorCode(error: Throwable): String = when (error) {
    is CloudApiException -> error.code
    is IOException -> "network_error"
    else -> "sync_failed"
}

private fun moveReplacing(source: File, destination: File) {
    try {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: Exception) {
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
