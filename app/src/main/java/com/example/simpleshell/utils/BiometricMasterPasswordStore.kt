package com.example.simpleshell.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricMasterPasswordStore {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val PREFS_NAME = "biometric_master_passwords"
    private const val PREF_ENTRY_PREFIX = "entry_"
    private const val KEY_ALIAS_PREFIX = "simpleshell_master_password_"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AUTH_TAG_LENGTH_BITS = 128

    fun hasStoredSecret(context: Context, scope: String): Boolean {
        val key = prefKey(scope)
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(key)
    }

    fun createEncryptionCryptoObject(scope: String): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(scope))
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun createDecryptionCryptoObject(context: Context, scope: String): BiometricPrompt.CryptoObject? {
        val payload = loadPayload(context, scope) ?: return null
        return try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(scope),
                GCMParameterSpec(AUTH_TAG_LENGTH_BITS, payload.iv)
            )
            BiometricPrompt.CryptoObject(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            clearSecret(context, scope)
            null
        } catch (_: Exception) {
            clearSecret(context, scope)
            null
        }
    }

    fun saveSecret(
        context: Context,
        scope: String,
        plainText: String,
        cipher: Cipher
    ) {
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = StoredPayload(
            iv = cipher.iv,
            cipherText = cipherBytes
        )

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(prefKey(scope), payload.serialize())
            .apply()
    }

    fun decryptSecret(
        context: Context,
        scope: String,
        cipher: Cipher
    ): String {
        val payload = loadPayload(context, scope) ?: error("Saved biometric master password not found")
        val plainBytes = cipher.doFinal(payload.cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun clearSecret(context: Context, scope: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(prefKey(scope))
            .apply()

        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.deleteEntry(keyAlias(scope))
        }
    }

    fun clearAll(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith(PREF_ENTRY_PREFIX) }
        for (prefKey in keys) {
            val scopeHash = prefKey.removePrefix(PREF_ENTRY_PREFIX)
            prefs.edit().remove(prefKey).apply()
            runCatching {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                keyStore.deleteEntry(KEY_ALIAS_PREFIX + scopeHash)
            }
        }
    }

    private fun loadPayload(context: Context, scope: String): StoredPayload? {
        val serialized = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(prefKey(scope), null)
            ?: return null
        return StoredPayload.deserialize(serialized)
    }

    private fun getOrCreateSecretKey(scope: String): SecretKey {
        val alias = keyAlias(scope)
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun prefKey(scope: String): String = PREF_ENTRY_PREFIX + scopeHash(scope)

    private fun keyAlias(scope: String): String = KEY_ALIAS_PREFIX + scopeHash(scope)

    private fun scopeHash(scope: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class StoredPayload(
        val iv: ByteArray,
        val cipherText: ByteArray
    ) {
        fun serialize(): String {
            val encoder = Base64.getEncoder()
            return encoder.encodeToString(iv) + ":" + encoder.encodeToString(cipherText)
        }

        companion object {
            fun deserialize(serialized: String): StoredPayload? {
                val parts = serialized.split(":")
                if (parts.size != 2) return null
                val decoder = Base64.getDecoder()
                return runCatching {
                    StoredPayload(
                        iv = decoder.decode(parts[0]),
                        cipherText = decoder.decode(parts[1])
                    )
                }.getOrNull()
            }
        }
    }
}
