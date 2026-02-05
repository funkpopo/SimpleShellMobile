package com.example.simpleshell.data.importing

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto compatibility layer for importing the PC app's config.json.
 *
 * PC project: D:\Projects\simpleshell\src\core\utils\crypto.js
 * - Algorithm: aes-256-cbc
 * - Key: sha256("simple-shell-encryption-key-12345")
 * - Format: ivHex:cipherHex
 *
 * On the JVM/Android side, "AES/CBC/PKCS5Padding" is compatible with Node's
 * PKCS#7 padding for AES block sizes.
 */
object SimpleShellPcCryptoCompat {
    // Keep this in sync with the PC application so imported configs can be decrypted.
    private const val ENCRYPTION_KEY = "simple-shell-encryption-key-12345"
    private const val IV_LENGTH_BYTES = 16

    private val keyBytes: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest(ENCRYPTION_KEY.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypts [value] if it doesn't already look like an "ivHex:cipherHex" payload.
     *
     * This is useful when we accept user input that might come from:
     * - plaintext UI fields
     * - imported PC config.json (already encrypted)
     */
    fun encryptMaybe(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return trimmed
        if (looksLikeEncryptedPayload(trimmed)) return trimmed
        return encryptTextOrNull(trimmed) ?: trimmed
    }

    /**
     * Attempts to decrypt [value] if it looks like an "ivHex:cipherHex" payload; otherwise returns it as-is.
     *
     * This is intentionally lenient to support:
     * - already-decrypted values
     * - blank strings
     * - unexpected formats without crashing the import flow
     */
    fun decryptMaybe(value: String): String {
        val trimmed = value.trim()
        if (!looksLikeEncryptedPayload(trimmed)) return trimmed
        return decryptTextOrNull(trimmed) ?: trimmed
    }

    fun encryptNullableMaybe(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return encryptMaybe(trimmed).ifBlank { null }
    }

    fun decryptNullableMaybe(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return decryptMaybe(trimmed).ifBlank { null }
    }

    /**
     * Encrypts plaintext into the "ivHex:cipherHex" format. Returns null on failure.
     */
    fun encryptTextOrNull(plainText: String): String? {
        val trimmed = plainText.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val iv = ByteArray(IV_LENGTH_BYTES)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

            val cipherBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            bytesToHexLower(iv) + ":" + bytesToHexLower(cipherBytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts a value in the "ivHex:cipherHex" format. Returns null on failure.
     */
    fun decryptTextOrNull(text: String): String? {
        val trimmed = text.trim()
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex <= 0 || colonIndex == trimmed.lastIndex) return null

        val ivHex = trimmed.substring(0, colonIndex)
        val cipherHex = trimmed.substring(colonIndex + 1)

        val iv = hexToBytesOrNull(ivHex) ?: return null
        if (iv.size != IV_LENGTH_BYTES) return null

        val cipherBytes = hexToBytesOrNull(cipherHex) ?: return null
        if (cipherBytes.isEmpty()) return null

        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun bytesToHexLower(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out[i++] = HEX_LOWER[v ushr 4]
            out[i++] = HEX_LOWER[v and 0x0F]
        }
        return String(out)
    }

    private fun looksLikeEncryptedPayload(text: String): Boolean {
        val colonIndex = text.indexOf(':')
        if (colonIndex <= 0 || colonIndex == text.lastIndex) return false
        val ivHex = text.substring(0, colonIndex)
        val cipherHex = text.substring(colonIndex + 1)
        if (ivHex.length != 32) return false // 16 bytes IV
        if (cipherHex.length < 2 || cipherHex.length % 2 != 0) return false
        return isHex(ivHex) && isHex(cipherHex)
    }

    private fun isHex(text: String): Boolean = text.all { c ->
        (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
    }

    private fun hexToBytesOrNull(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        if (!isHex(hex)) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                val hi = hex[i * 2].digitToInt(16)
                val lo = hex[i * 2 + 1].digitToInt(16)
                ((hi shl 4) or lo).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    private val HEX_LOWER = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )
}
