package com.example.simpleshell.ssh

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

internal data class MaterializedPrivateKey(
    val file: File,
    val isTemp: Boolean
)

/**
 * SSHJ expects a filesystem path when using [net.schmizz.sshj.SSHClient.loadKeys].
 *
 * In the UI we allow users to paste key *content* or select a key file. To keep the data model
 * simple (single string field) we "materialize" key content/URIs into a private temp file in
 * the app cache dir and then pass the absolute path to SSHJ.
 */
internal fun materializePrivateKey(context: Context, raw: String): MaterializedPrivateKey {
    val trimmed = raw.trim()
    require(trimmed.isNotEmpty()) { "privateKey is blank" }

    return when {
        looksLikeKeyContent(trimmed) -> {
            val tmp = File.createTempFile("simpleshell_key_", ".pem", context.cacheDir)
            tmp.writeText(trimmed)
            // Restrict readability to the app process as much as possible.
            tmp.setReadable(false, false)
            tmp.setReadable(true, true)
            MaterializedPrivateKey(file = tmp, isTemp = true)
        }

        trimmed.startsWith("content://") -> {
            val uri = trimmed.toUri()
            val tmp = File.createTempFile("simpleshell_key_", ".pem", context.cacheDir)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open key Uri" }
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            }
            tmp.setReadable(false, false)
            tmp.setReadable(true, true)
            MaterializedPrivateKey(file = tmp, isTemp = true)
        }

        trimmed.startsWith("file://") -> {
            val path = trimmed.toUri().path ?: trimmed.removePrefix("file://")
            MaterializedPrivateKey(file = File(path), isTemp = false)
        }

        else -> MaterializedPrivateKey(file = File(trimmed), isTemp = false)
    }
}

private fun looksLikeKeyContent(text: String): Boolean {
    // Most key contents contain newlines and/or a BEGIN header.
    if (text.contains('\n') || text.contains('\r')) return true
    if (text.contains("-----BEGIN")) return true
    if (text.contains("BEGIN OPENSSH PRIVATE KEY")) return true
    return false
}
