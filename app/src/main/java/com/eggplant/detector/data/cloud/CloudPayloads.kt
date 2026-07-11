package com.eggplant.detector.data.cloud

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun globalSharePayload(
    clientScanId: String,
    diseaseId: String,
    confidence: Float,
    source: String,
    modelVersion: String,
    photoPath: String,
): JsonObject = buildJsonObject {
    put("clientScanId", clientScanId)
    put("diseaseId", diseaseId)
    put("confidence", confidence)
    put("source", source)
    put("modelVersion", modelVersion)
    put("photoPath", photoPath)
}

internal fun sharingConsentPayload(enabled: Boolean): JsonObject = buildJsonObject {
    put("enabled", enabled)
    if (enabled) put("consentVersion", 1)
}

internal fun diseaseRequestPayload(
    clientRequestId: String,
    requestedName: String?,
    notes: String?,
    modelVersion: String,
    photoPaths: List<String>,
    photoSources: List<String>,
    rightsConsent: Boolean,
    trainingConsent: Boolean,
): JsonObject = buildJsonObject {
    put("clientRequestId", clientRequestId)
    requestedName?.takeIf(String::isNotBlank)?.let { put("requestedName", it) }
    notes?.let { put("notes", it) }
    put("modelVersion", modelVersion)
    put("rightsConsent", rightsConsent)
    put("trainingConsent", trainingConsent)
    put("photoPaths", buildJsonArray { photoPaths.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
    put("photoSources", buildJsonArray { photoSources.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
}

internal fun JsonObject.photoPaths(): List<String> = getValue("photoPaths").jsonArray.map { it.jsonPrimitive.content }

internal fun JsonObject.photoSources(): List<String> = getValue("photoSources").jsonArray.map { it.jsonPrimitive.content }

internal fun File.sha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
