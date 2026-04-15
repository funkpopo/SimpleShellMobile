package com.example.simpleshell.data.importing

import android.content.Context
import android.content.SharedPreferences
import org.bouncycastle.crypto.generators.SCrypt
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SimpleShellPcSecurityConfig(
    val randomKey: String,
    val masterPasswordEnabled: Boolean,
    val masterPasswordVerifier: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("randomKey", randomKey)
        put("masterPasswordEnabled", masterPasswordEnabled)
        put("masterPasswordVerifier", masterPasswordVerifier)
    }

    companion object {
        fun fromJson(json: JSONObject?): SimpleShellPcSecurityConfig {
            requireNotNull(json) { "config.json is missing security settings" }

            val randomKey = json.optString("randomKey", "").trim()
            require(randomKey.isNotEmpty()) { "security.randomKey is required" }

            val verifier = json.optString("masterPasswordVerifier", "").trim()
            val enabled = json.optBoolean("masterPasswordEnabled", false) && verifier.isNotEmpty()

            return SimpleShellPcSecurityConfig(
                randomKey = randomKey,
                masterPasswordEnabled = enabled,
                masterPasswordVerifier = if (enabled) verifier else ""
            )
        }

        fun createDefault(): SimpleShellPcSecurityConfig = SimpleShellPcSecurityConfig(
            randomKey = generateRandomKey(),
            masterPasswordEnabled = false,
            masterPasswordVerifier = ""
        )
    }
}

class SimpleShellPcCredentialLockedException(
    val securityRandomKey: String,
    message: String
) : IllegalStateException(message)

class SimpleShellPcInvalidMasterPasswordException(
    val securityRandomKey: String,
    message: String
) : IllegalArgumentException(message)

/**
 * Desktop SimpleShell credential cipher.
 *
 * PC project: D:\Projects\simpleshell\src\core\utils\crypto.js
 * - Key when master password is disabled: sha256("SimpleShellCredentialStore:<randomKey>")
 * - Key when master password is enabled: scrypt(masterPassword, "SimpleShellCredentialStore:<randomKey>", 32)
 * - Payload: ssv2:<12-byte-iv-hex>:<16-byte-gcm-tag-hex>:<ciphertext-hex>
 */
class SimpleShellPcCredentialCipher internal constructor(
    private val securityConfig: SimpleShellPcSecurityConfig,
    activeKey: ByteArray
) {
    private val keyBytes: ByteArray = activeKey.copyOf()

    fun encryptNullable(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return encryptMaybe(trimmed).ifBlank { null }
    }

    fun decryptNullable(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return decryptText(trimmed).ifBlank { null }
    }

    fun encryptMaybe(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        if (looksLikeSsv2Payload(trimmed)) {
            decryptText(trimmed)
            return trimmed
        }
        require(!looksLikeLegacyCbcPayload(trimmed)) {
            "Unsupported legacy SimpleShell credential payload"
        }
        return encryptText(trimmed)
    }

    fun encryptText(text: String): String {
        val plainText = text.trim()
        if (plainText.isEmpty()) return ""

        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv)
        )

        val sealedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        require(sealedBytes.size >= AUTH_TAG_LENGTH_BYTES) { "AES-GCM output is invalid" }

        val cipherBytes = sealedBytes.copyOfRange(0, sealedBytes.size - AUTH_TAG_LENGTH_BYTES)
        val authTag = sealedBytes.copyOfRange(sealedBytes.size - AUTH_TAG_LENGTH_BYTES, sealedBytes.size)

        return listOf(
            PAYLOAD_VERSION,
            bytesToHexLower(iv),
            bytesToHexLower(authTag),
            bytesToHexLower(cipherBytes)
        ).joinToString(":")
    }

    fun decryptText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val parts = trimmed.split(":")
        require(parts.size == 4 && parts[0] == PAYLOAD_VERSION) {
            "Unsupported SimpleShell credential payload"
        }

        val iv = hexToBytes(parts[1], "iv")
        require(iv.size == IV_LENGTH_BYTES) { "Invalid SimpleShell credential iv length" }

        val authTag = hexToBytes(parts[2], "authTag")
        require(authTag.size == AUTH_TAG_LENGTH_BYTES) {
            "Invalid SimpleShell credential auth tag length"
        }

        val cipherBytes = hexToBytes(parts[3], "ciphertext")
        val sealedBytes = cipherBytes + authTag

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv)
        )

        return String(cipher.doFinal(sealedBytes), Charsets.UTF_8)
    }

    fun securityJson(): JSONObject = securityConfig.toJson()
}

object SimpleShellPcCryptoCompat {
    private const val PREFS_NAME = "simpleshell_pc_security"
    private const val PREF_RANDOM_KEY = "random_key"
    private const val PREF_MASTER_PASSWORD_ENABLED = "master_password_enabled"
    private const val PREF_MASTER_PASSWORD_VERIFIER = "master_password_verifier"

    private val monitor = Any()
    private var sharedPreferences: SharedPreferences? = null
    private var securityConfig: SimpleShellPcSecurityConfig? = null
    private var activeKey: ByteArray? = null

    fun initialize(context: Context) {
        synchronized(monitor) {
            if (sharedPreferences == null) {
                sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            val config = loadOrCreateSecurityConfig()
            configureInMemory(config)
        }
    }

    fun getSecurityConfig(): SimpleShellPcSecurityConfig = synchronized(monitor) {
        val config = securityConfig ?: loadOrCreateSecurityConfig().also(::configureInMemory)
        config
    }

    fun getSecurityConfigJson(): JSONObject = getSecurityConfig().toJson()

    fun getCredentialSecurityStatus(): SimpleShellPcCredentialSecurityStatus = synchronized(monitor) {
        val config = securityConfig ?: loadOrCreateSecurityConfig().also(::configureInMemory)
        SimpleShellPcCredentialSecurityStatus(
            randomKeyConfigured = config.randomKey.isNotEmpty(),
            masterPasswordEnabled = config.masterPasswordEnabled,
            unlocked = activeKey != null,
            requiresUnlock = config.masterPasswordEnabled && activeKey == null
        )
    }

    fun unlockWithMasterPassword(masterPassword: String): SimpleShellPcCredentialSecurityStatus {
        synchronized(monitor) {
            val config = securityConfig ?: loadOrCreateSecurityConfig().also(::configureInMemory)
            if (!config.masterPasswordEnabled) {
                activeKey = deriveEncryptionKey(config.randomKey)
                return getCredentialSecurityStatus()
            }

            val derivedKey = deriveEncryptionKey(config.randomKey, masterPassword)
            val actualVerifier = createMasterPasswordVerifier(derivedKey)
            if (!constantTimeHexEquals(config.masterPasswordVerifier, actualVerifier)) {
                throw SimpleShellPcInvalidMasterPasswordException(config.randomKey, "Invalid master password")
            }

            activeKey = derivedKey
            return getCredentialSecurityStatus()
        }
    }

    fun replaceSecurityConfig(config: SimpleShellPcSecurityConfig, masterPassword: String? = null) {
        synchronized(monitor) {
            val nextKey = deriveAndVerifyKey(config, masterPassword)
            persistSecurityConfig(config)
            securityConfig = config
            activeKey = nextKey
        }
    }

    fun createCipher(
        config: SimpleShellPcSecurityConfig,
        masterPassword: String? = null
    ): SimpleShellPcCredentialCipher {
        val key = deriveAndVerifyKey(config, masterPassword)
        return SimpleShellPcCredentialCipher(config, key)
    }

    fun createCipherFromConfigJson(
        securityJson: JSONObject?,
        masterPassword: String? = null
    ): SimpleShellPcCredentialCipher {
        return createCipher(SimpleShellPcSecurityConfig.fromJson(securityJson), masterPassword)
    }

    fun encryptNullableMaybe(value: String?): String? = activeCipher().encryptNullable(value)

    fun decryptNullableMaybe(value: String?): String? = activeCipher().decryptNullable(value)

    fun encryptMaybe(value: String): String = activeCipher().encryptMaybe(value)

    fun decryptMaybe(value: String): String = activeCipher().decryptText(value)

    fun looksLikeEncryptedPayload(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        return looksLikeSsv2Payload(trimmed)
    }

    private fun activeCipher(): SimpleShellPcCredentialCipher {
        synchronized(monitor) {
            val config = securityConfig ?: loadOrCreateSecurityConfig().also(::configureInMemory)
            val key = activeKey ?: throw SimpleShellPcCredentialLockedException(config.randomKey, "Credential store is locked")
            return SimpleShellPcCredentialCipher(config, key)
        }
    }

    private fun loadOrCreateSecurityConfig(): SimpleShellPcSecurityConfig {
        val prefs = sharedPreferences
        if (prefs == null) {
            return SimpleShellPcSecurityConfig.createDefault()
        }

        val randomKey = prefs.getString(PREF_RANDOM_KEY, "").orEmpty().trim()
        val masterPasswordEnabled = prefs.getBoolean(PREF_MASTER_PASSWORD_ENABLED, false)
        val verifier = prefs.getString(PREF_MASTER_PASSWORD_VERIFIER, "").orEmpty().trim()

        val config = if (randomKey.isNotEmpty()) {
            SimpleShellPcSecurityConfig(
                randomKey = randomKey,
                masterPasswordEnabled = masterPasswordEnabled && verifier.isNotEmpty(),
                masterPasswordVerifier = if (masterPasswordEnabled && verifier.isNotEmpty()) verifier else ""
            )
        } else {
            SimpleShellPcSecurityConfig.createDefault()
        }

        if (randomKey.isEmpty()) {
            persistSecurityConfig(config)
        }

        return config
    }

    private fun configureInMemory(config: SimpleShellPcSecurityConfig) {
        securityConfig = config
        activeKey = if (config.masterPasswordEnabled) null else deriveEncryptionKey(config.randomKey)
    }

    private fun persistSecurityConfig(config: SimpleShellPcSecurityConfig) {
        sharedPreferences?.edit()
            ?.putString(PREF_RANDOM_KEY, config.randomKey)
            ?.putBoolean(PREF_MASTER_PASSWORD_ENABLED, config.masterPasswordEnabled)
            ?.putString(PREF_MASTER_PASSWORD_VERIFIER, config.masterPasswordVerifier)
            ?.apply()
    }
}

data class SimpleShellPcCredentialSecurityStatus(
    val randomKeyConfigured: Boolean,
    val masterPasswordEnabled: Boolean,
    val unlocked: Boolean,
    val requiresUnlock: Boolean
)

private const val RANDOM_KEY_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
private const val RANDOM_KEY_LENGTH = 32
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_LENGTH_BYTES = 12
private const val AUTH_TAG_LENGTH_BYTES = 16
private const val AUTH_TAG_LENGTH_BITS = AUTH_TAG_LENGTH_BYTES * 8
private const val PAYLOAD_VERSION = "ssv2"
private const val MASTER_PASSWORD_VERIFIER_LABEL = "SimpleShellMasterPasswordVerifier"
private const val SECURITY_CONTEXT_PREFIX = "SimpleShellCredentialStore"

private fun generateRandomKey(length: Int = RANDOM_KEY_LENGTH): String {
    val normalizedLength = length.coerceAtLeast(1)
    val acceptedByteUpperBound = (256 / RANDOM_KEY_CHARSET.length) * RANDOM_KEY_CHARSET.length
    val random = SecureRandom()
    val out = StringBuilder(normalizedLength)
    val buffer = ByteArray(normalizedLength * 2)

    while (out.length < normalizedLength) {
        random.nextBytes(buffer)
        for (byte in buffer) {
            val value = byte.toInt() and 0xFF
            if (value >= acceptedByteUpperBound) continue
            out.append(RANDOM_KEY_CHARSET[value % RANDOM_KEY_CHARSET.length])
            if (out.length >= normalizedLength) break
        }
    }

    return out.toString()
}

private fun deriveAndVerifyKey(
    config: SimpleShellPcSecurityConfig,
    masterPassword: String?
): ByteArray {
    require(config.randomKey.isNotBlank()) { "security.randomKey is required" }

    if (!config.masterPasswordEnabled) {
        return deriveEncryptionKey(config.randomKey)
    }

    if (masterPassword.isNullOrEmpty()) {
        throw SimpleShellPcCredentialLockedException(config.randomKey, "Master password is required")
    }

    val derivedKey = deriveEncryptionKey(config.randomKey, masterPassword)
    val actualVerifier = createMasterPasswordVerifier(derivedKey)
    if (!constantTimeHexEquals(config.masterPasswordVerifier, actualVerifier)) {
        throw SimpleShellPcInvalidMasterPasswordException(config.randomKey, "Invalid master password")
    }
    return derivedKey
}

private fun deriveEncryptionKey(randomKey: String, masterPassword: String = ""): ByteArray {
    val salt = "$SECURITY_CONTEXT_PREFIX:$randomKey".toByteArray(Charsets.UTF_8)
    return if (masterPassword.isNotEmpty()) {
        SCrypt.generate(masterPassword.toByteArray(Charsets.UTF_8), salt, 16_384, 8, 1, 32)
    } else {
        MessageDigest.getInstance("SHA-256").digest(salt)
    }
}

private fun createMasterPasswordVerifier(derivedKey: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(derivedKey, "HmacSHA256"))
    return bytesToHexLower(mac.doFinal(MASTER_PASSWORD_VERIFIER_LABEL.toByteArray(Charsets.UTF_8)))
}

private fun constantTimeHexEquals(expectedHex: String, actualHex: String): Boolean {
    val expected = hexToBytesOrNull(expectedHex) ?: return false
    val actual = hexToBytesOrNull(actualHex) ?: return false
    return MessageDigest.isEqual(expected, actual)
}

private fun looksLikeSsv2Payload(text: String): Boolean {
    val parts = text.split(":")
    if (parts.size != 4 || parts[0] != PAYLOAD_VERSION) return false
    return parts[1].length == IV_LENGTH_BYTES * 2 &&
        parts[2].length == AUTH_TAG_LENGTH_BYTES * 2 &&
        parts[3].isNotEmpty() &&
        parts[3].length % 2 == 0 &&
        isHex(parts[1]) &&
        isHex(parts[2]) &&
        isHex(parts[3])
}

private fun looksLikeLegacyCbcPayload(text: String): Boolean {
    val parts = text.split(":")
    if (parts.size != 2) return false
    val ivHex = parts[0]
    val cipherHex = parts[1]
    return ivHex.length == 32 &&
        cipherHex.isNotEmpty() &&
        cipherHex.length % 2 == 0 &&
        isHex(ivHex) &&
        isHex(cipherHex)
}

private fun hexToBytes(hex: String, fieldName: String): ByteArray {
    return hexToBytesOrNull(hex) ?: throw IllegalArgumentException("Invalid SimpleShell credential $fieldName")
}

private fun hexToBytesOrNull(hex: String): ByteArray? {
    if (hex.length % 2 != 0 || !isHex(hex)) return null
    return try {
        ByteArray(hex.length / 2) { i ->
            val hi = hex[i * 2].digitToInt(16)
            val lo = hex[i * 2 + 1].digitToInt(16)
            ((hi shl 4) or lo).toByte()
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun bytesToHexLower(bytes: ByteArray): String {
    val out = CharArray(bytes.size * 2)
    var i = 0
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        out[i++] = HEX_LOWER[value ushr 4]
        out[i++] = HEX_LOWER[value and 0x0F]
    }
    return String(out)
}

private fun isHex(text: String): Boolean = text.all { char ->
    char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}

private val HEX_LOWER = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
)
