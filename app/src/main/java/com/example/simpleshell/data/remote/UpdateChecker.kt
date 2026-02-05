package com.example.simpleshell.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val body: String
)

sealed class UpdateCheckResult {
    data class NewVersionAvailable(val releaseInfo: ReleaseInfo) : UpdateCheckResult()
    data object AlreadyLatest : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

@Singleton
class UpdateChecker @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext UpdateCheckResult.Error("请求失败: ${response.code}")
            }

            val body = response.body?.string()
                ?: return@withContext UpdateCheckResult.Error("响应为空")

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            val latestVersion = tagName.removePrefix("v")

            val releaseInfo = ReleaseInfo(
                tagName = tagName,
                name = json.optString("name", tagName),
                htmlUrl = json.getString("html_url"),
                body = json.optString("body", "")
            )

            if (isNewerVersion(latestVersion, currentVersion)) {
                UpdateCheckResult.NewVersionAvailable(releaseInfo)
            } else {
                UpdateCheckResult.AlreadyLatest
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("检查更新失败: ${e.message}")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    companion object {
        private const val GITHUB_API_URL =
            "https://api.github.com/repos/funkpopo/simpleshellmobile/releases/latest"
    }
}
