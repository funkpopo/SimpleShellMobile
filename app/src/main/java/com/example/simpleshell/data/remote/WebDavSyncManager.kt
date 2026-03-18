package com.example.simpleshell.data.remote

import android.util.Base64
import com.example.simpleshell.data.importing.SimpleShellPcConfigExporter
import com.example.simpleshell.data.importing.SimpleShellPcConfigImporter
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavSyncManager @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val pcConfigExporter: SimpleShellPcConfigExporter,
    private val pcConfigImporter: SimpleShellPcConfigImporter,
    private val okHttpClient: OkHttpClient
) {
    suspend fun backup(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefs = userPreferencesRepository.preferences.first()
            val url = prefs.webDavUrl.trimEnd('/')
            if (url.isEmpty()) return@withContext Result.failure(Exception("WebDAV URL is empty"))

            val username = prefs.webDavUsername
            val password = prefs.webDavPassword
            val authHeader = "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)

            val configJson = pcConfigExporter.exportToConfigJson()
            val requestBody = configJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$url/config.json")
                .put(requestBody)
                .header("Authorization", authHeader)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restore(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefs = userPreferencesRepository.preferences.first()
            val url = prefs.webDavUrl.trimEnd('/')
            if (url.isEmpty()) return@withContext Result.failure(Exception("WebDAV URL is empty"))

            val username = prefs.webDavUsername
            val password = prefs.webDavPassword
            val authHeader = "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)

            val request = Request.Builder()
                .url("$url/config.json")
                .get()
                .header("Authorization", authHeader)
                .build()

            val configJson = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            }

            pcConfigImporter.importFromConfigJson(configJson)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
