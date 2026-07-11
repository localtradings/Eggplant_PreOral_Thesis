package com.eggplant.detector.domain.model

enum class MotionPreference { SYSTEM, FULL, REDUCED }

sealed interface LiveDetectionState {
    data object Idle : LiveDetectionState
    data object Preparing : LiveDetectionState
    data class Provisional(val diseaseName: String, val confidence: Float) : LiveDetectionState
    data class Confirmed(val diseaseName: String, val confidence: Float) : LiveDetectionState
    data class Error(val code: String, val message: String) : LiveDetectionState
}

sealed interface ShareEligibility {
    data object Eligible : ShareEligibility
    data class Ineligible(val reason: Reason) : ShareEligibility

    enum class Reason { SHARING_DISABLED, UNSUPPORTED_RESULT, LOW_CONFIDENCE, GALLERY_SOURCE, PHOTO_UNAVAILABLE, NOT_CONFIRMED }
}

enum class SyncOutboxState { PENDING, UPLOADING, RETRY, COMPLETED, FAILED, CANCELLED }

data class SyncOutboxEvent(
    val id: String,
    val eventType: String,
    val version: Int,
    val idempotencyKey: String,
    val attempts: Int,
    val nextAttemptAt: String,
    val state: SyncOutboxState,
    val lastErrorCode: String? = null,
)

data class GlobalScan(
    val id: String,
    val diseaseId: String,
    val diseaseName: String,
    val confidence: Int,
    val photoPath: String?,
    val publishedAt: String,
    val symptoms: List<String>,
    val causes: String,
    val prevention: String,
    val guidance: String,
    val whenToAct: String,
    val disclaimer: String,
    val references: List<DiseaseReference>,
)

data class GlobalRanking(val diseaseId: String, val diseaseName: String, val scanCount: Long)

data class GlobalFeedState(
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val lastUpdatedAt: String? = null,
    val lastErrorCode: String? = null,
)

sealed interface CloudDeletionState {
    data object Idle : CloudDeletionState
    data object Queued : CloudDeletionState
    data object Processing : CloudDeletionState
    data class Completed(val contributionCount: Int) : CloudDeletionState
    data class Failed(val code: String?) : CloudDeletionState
}

data class DiseaseRequest(
    val id: String,
    val requestedName: String?,
    val notes: String?,
    val status: String,
    val photoPaths: List<String>,
    val adminNote: String?,
    val createdAt: String,
    val uploadProgress: Float,
)

sealed interface UploadProgress {
    data object Queued : UploadProgress
    data class Uploading(val completed: Int, val total: Int) : UploadProgress
    data object Complete : UploadProgress
    data class Failed(val code: String, val canRetry: Boolean) : UploadProgress
    data object Cancelled : UploadProgress
}
