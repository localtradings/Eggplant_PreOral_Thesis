package com.eggplant.detector.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["state", "nextAttemptAt"]),
    ],
    primaryKeys = ["id"],
)
data class SyncOutboxEntity(
    val id: String,
    val eventType: String,
    val version: Int,
    val idempotencyKey: String,
    val payloadJson: String,
    val state: String,
    val attempts: Int,
    val nextAttemptAt: String,
    val lastErrorCode: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "global_scan_cache",
    indices = [Index("publishedAt"), Index("diseaseId")],
    primaryKeys = ["id"],
)
data class GlobalScanCacheEntity(
    val id: String,
    val diseaseId: String,
    val confidence: Float,
    val source: String,
    val modelVersion: String,
    val cachedPhotoPath: String?,
    val publishedAt: String,
    val expiresAt: String,
    val contentJson: String,
)

@Entity(tableName = "global_ranking_cache", primaryKeys = ["diseaseId"])
data class GlobalRankingCacheEntity(
    val diseaseId: String,
    val scanCount: Long,
    val updatedAt: String,
)

@Entity(
    tableName = "disease_requests",
    indices = [Index(value = ["clientRequestId"], unique = true), Index("createdAt")],
    primaryKeys = ["id"],
)
data class DiseaseRequestEntity(
    val id: String,
    val clientRequestId: String,
    val requestedName: String,
    val notes: String?,
    val modelVersion: String,
    val rightsConsent: Boolean,
    val trainingConsent: Boolean = false,
    val state: String,
    val uploadProgress: Float,
    val adminNote: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "disease_request_photos",
    primaryKeys = ["requestId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DiseaseRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["requestId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("requestId")],
)
data class DiseaseRequestPhotoEntity(
    val requestId: String,
    val position: Int,
    val localPhotoPath: String,
    val remotePath: String?,
    val uploadState: String,
    val sha256: String,
    val sizeBytes: Long,
)
