package com.example.simpleshell.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleShellPcCryptoCompatTest {

    @Test
    fun decryptTextOrNull_decryptsNodeAes256CbcPayload() {
        // Generated via Node.js using the PC project's algorithm with:
        // key = sha256("simple-shell-encryption-key-12345")
        // iv  = 000102030405060708090a0b0c0d0e0f
        // plaintext = "hello-world"
        val encrypted = "000102030405060708090a0b0c0d0e0f:a3b92e1434a53ebf539b43bc77e80839"

        val decrypted = SimpleShellPcCryptoCompat.decryptTextOrNull(encrypted)

        assertEquals("hello-world", decrypted)
    }

    @Test
    fun decryptMaybe_returnsPlaintextAsIs() {
        assertEquals("plain", SimpleShellPcCryptoCompat.decryptMaybe("plain"))
        assertEquals("", SimpleShellPcCryptoCompat.decryptMaybe(""))
        assertEquals("plain", SimpleShellPcCryptoCompat.decryptMaybe("  plain  "))
    }

    @Test
    fun decryptTextOrNull_returnsNullOnInvalidFormat() {
        assertNull(SimpleShellPcCryptoCompat.decryptTextOrNull("not-an-encrypted-payload"))
        assertNull(SimpleShellPcCryptoCompat.decryptTextOrNull("abc:def")) // not hex / invalid iv length
        assertNull(SimpleShellPcCryptoCompat.decryptTextOrNull("0011:")) // missing ciphertext
    }

    @Test
    fun encryptTextOrNull_roundTrips() {
        val plain = "secret-password-123"
        val encrypted = SimpleShellPcCryptoCompat.encryptTextOrNull(plain)
        requireNotNull(encrypted)

        // Basic shape: ivHex:cipherHex (16-byte IV => 32 hex chars)
        val parts = encrypted.split(":")
        assertEquals(2, parts.size)
        assertEquals(32, parts[0].length)
        assertTrue(parts[1].isNotEmpty())

        val decrypted = SimpleShellPcCryptoCompat.decryptTextOrNull(encrypted)
        assertEquals(plain, decrypted)
    }

    @Test
    fun encryptMaybe_doesNotDoubleEncrypt() {
        val encryptedFromNode = "000102030405060708090a0b0c0d0e0f:a3b92e1434a53ebf539b43bc77e80839"
        assertEquals(encryptedFromNode, SimpleShellPcCryptoCompat.encryptMaybe(encryptedFromNode))
        assertEquals("hello-world", SimpleShellPcCryptoCompat.decryptMaybe(encryptedFromNode))
    }
}
