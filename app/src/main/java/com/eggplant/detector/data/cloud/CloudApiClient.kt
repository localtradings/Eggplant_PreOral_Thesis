package com.eggplant.detector.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.eggplant.detector.BuildConfig
import java.security.KeyStore
import java.time.Instant
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

data class CloudConfiguration(
    val apiBaseUrl: String,
    val supabaseUrl: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean get() = apiBaseUrl.startsWith("https://") && supabaseUrl.startsWith("https://") && publishableKey.isNotBlank()

    companion object {
        fun release() = CloudConfiguration(
            BuildConfig.CLOUD_API_BASE_URL.trimEnd('/'),
            BuildConfig.SUPABASE_URL.trimEnd('/'),
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }
}

data class CloudSession(val accessToken: String, val refreshToken: String, val expiresAtEpochSeconds: Long)

data class ConditionalCloudResponse(
    val status: Int,
    val body: JsonObject?,
    val etag: String?,
)

class CloudApiException(val status: Int, val code: String, message: String) : java.io.IOException(message)

class CloudApiClient(
    context: Context,
    private val configuration: CloudConfiguration = CloudConfiguration.release(),
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val tokenVault = TokenVault(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val authLock = Any()

    val isConfigured: Boolean get() = configuration.isConfigured

    suspend fun get(path: String): JsonObject = requireNotNull(authorizedRequest("GET", path, null).body)
    suspend fun getConditional(path: String, etag: String?): ConditionalCloudResponse =
        authorizedRequest("GET", path, null, etag)
    suspend fun post(path: String, body: JsonObject): JsonObject =
        requireNotNull(authorizedRequest("POST", path, body).body)

    suspend fun upload(signedUrl: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val url = when {
            signedUrl.startsWith("https://") -> signedUrl
            signedUrl.startsWith("/") -> configuration.supabaseUrl + signedUrl
            else -> configuration.supabaseUrl + "/" + signedUrl
        }
        val request = Request.Builder()
            .url(url)
            .put(bytes.toRequestBody(JPEG))
            .header("Content-Type", "image/jpeg")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw CloudApiException(response.code, "upload_failed", "Photo upload failed.")
        }
    }

    suspend fun download(signedUrl: String, destination: File, maximumBytes: Long) = withContext(Dispatchers.IO) {
        require(maximumBytes > 0)
        val allowedHost = configuration.supabaseUrl.toHttpUrl().host
        val url = signedUrl.toHttpUrl()
        require(url.isHttps && url.host == allowedHost) { "The cloud photo URL is not trusted." }
        destination.parentFile?.mkdirs()
        try {
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw CloudApiException(response.code, "photo_download_failed", "Community photo download failed.")
                }
                check(response.request.url.isHttps && response.request.url.host == allowedHost) {
                    "The cloud photo redirect is not trusted."
                }
                val declaredLength = response.body.contentLength()
                check(declaredLength < 0 || declaredLength <= maximumBytes) { "Community photo exceeds the size limit." }
                response.body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= maximumBytes) { "Community photo exceeds the size limit." }
                            output.write(buffer, 0, count)
                        }
                        check(total > 0) { "Community photo is empty." }
                    }
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun clearSession() = tokenVault.clear()

    private suspend fun authorizedRequest(
        method: String,
        path: String,
        body: JsonObject?,
        ifNoneMatch: String? = null,
    ): ConditionalCloudResponse = withContext(Dispatchers.IO) {
        check(configuration.isConfigured) { "Cloud is not configured for this build." }
        var session = ensureSession()
        repeat(2) { attempt ->
            val requestBuilder = Request.Builder()
                .url(configuration.apiBaseUrl + path)
                .header("Authorization", "Bearer ${session.accessToken}")
                .header("Accept", "application/json")
                .method(method, body?.toString()?.toRequestBody(JSON_MEDIA_TYPE))
            ifNoneMatch?.takeIf(String::isNotBlank)?.let { requestBuilder.header("If-None-Match", it) }
            val request = requestBuilder.build()
            http.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (response.code == 401 && attempt == 0) {
                    session = refreshSession(session)
                } else if (response.code == 304) {
                    return@withContext ConditionalCloudResponse(304, null, response.header("ETag") ?: ifNoneMatch)
                } else if (!response.isSuccessful) {
                    val error = runCatching { json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonObject }.getOrNull()
                    throw CloudApiException(
                        response.code,
                        error?.get("code")?.jsonPrimitive?.content ?: "cloud_request_failed",
                        error?.get("message")?.jsonPrimitive?.content ?: "Cloud request failed.",
                    )
                } else {
                    return@withContext ConditionalCloudResponse(
                        status = response.code,
                        body = if (responseBody.isBlank()) buildJsonObject {} else json.parseToJsonElement(responseBody).jsonObject,
                        etag = response.header("ETag"),
                    )
                }
            }
        }
        error("Cloud authentication retry exhausted.")
    }

    private fun ensureSession(): CloudSession = synchronized(authLock) {
        val current = tokenVault.load()
        val now = Instant.now().epochSecond
        if (current != null && current.expiresAtEpochSeconds > now + 60) return@synchronized current
        val session = current?.let(::renewSession) ?: authenticate("/auth/v1/signup", buildJsonObject {})
        tokenVault.save(session)
        session
    }

    private fun refreshSession(fallback: CloudSession): CloudSession = synchronized(authLock) {
        val session = renewSession(tokenVault.load() ?: fallback)
        tokenVault.save(session)
        session
    }

    private fun renewSession(current: CloudSession): CloudSession = try {
        authenticate(
            "/auth/v1/token?grant_type=refresh_token",
            buildJsonObject { put("refresh_token", current.refreshToken) },
        )
    } catch (error: CloudApiException) {
        if (error.status != 400 && error.status != 401) throw error
        tokenVault.clear()
        authenticate("/auth/v1/signup", buildJsonObject {})
    }

    private fun authenticate(path: String, body: JsonObject): CloudSession {
        val request = Request.Builder()
            .url(configuration.supabaseUrl + path)
            .header("apikey", configuration.publishableKey)
            .header("Authorization", "Bearer ${configuration.publishableKey}")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) throw CloudApiException(response.code, "anonymous_auth_failed", "Anonymous cloud sign-in failed.")
            val objectValue = json.parseToJsonElement(responseBody).jsonObject
            return CloudSession(
                accessToken = objectValue.getValue("access_token").jsonPrimitive.content,
                refreshToken = objectValue.getValue("refresh_token").jsonPrimitive.content,
                expiresAtEpochSeconds = Instant.now().epochSecond + objectValue.getValue("expires_in").jsonPrimitive.content.toLong(),
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val JPEG = "image/jpeg".toMediaType()
    }
}

private class TokenVault(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_auth_encrypted", Context.MODE_PRIVATE)

    fun load(): CloudSession? = runCatching {
        val ciphertext = preferences.getString("ciphertext", null) ?: return null
        val iv = preferences.getString("iv", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        val plain = String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8).split('\n')
        if (plain.size != 3) return null
        CloudSession(plain[0], plain[1], plain[2].toLong())
    }.getOrNull()

    fun save(session: CloudSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val plain = listOf(session.accessToken, session.refreshToken, session.expiresAtEpochSeconds).joinToString("\n").toByteArray()
        preferences.edit()
            .putString("ciphertext", Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clear() = preferences.edit().clear().apply()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "eggplant_cloud_auth_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
