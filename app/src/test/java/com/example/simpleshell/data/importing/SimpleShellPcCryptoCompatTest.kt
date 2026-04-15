package com.example.simpleshell.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleShellPcCryptoCompatTest {
    @Test
    fun decryptsDesktopMasterPasswordPayload() {
        val config = SimpleShellPcSecurityConfig(
            randomKey = "abcdefghijklmnopqrstuvwxyzABCDEF",
            masterPasswordEnabled = true,
            masterPasswordVerifier = "3b580f7574f34230b267e684d6163a8257da40926048153a0579df2c58421f44"
        )
        val desktopPayload =
            "ssv2:00112233445566778899aabb:33a50eb6dfc11b23e8715296c7e6354a:bc9d8c805fdf88e1"

        val cipher = SimpleShellPcCryptoCompat.createCipher(config, "secret123")

        assertEquals("p@ssw0rd", cipher.decryptText(desktopPayload))
        assertEquals(desktopPayload, cipher.encryptMaybe(desktopPayload))
    }

    @Test(expected = SimpleShellPcInvalidMasterPasswordException::class)
    fun rejectsInvalidDesktopMasterPassword() {
        val config = SimpleShellPcSecurityConfig(
            randomKey = "abcdefghijklmnopqrstuvwxyzABCDEF",
            masterPasswordEnabled = true,
            masterPasswordVerifier = "3b580f7574f34230b267e684d6163a8257da40926048153a0579df2c58421f44"
        )

        SimpleShellPcCryptoCompat.createCipher(config, "wrong-password")
    }

    @Test
    fun encryptsAndDecryptsSsv2WithoutMasterPassword() {
        val config = SimpleShellPcSecurityConfig(
            randomKey = "0123456789ABCDEFGHIJKLMNOPQRSTUV",
            masterPasswordEnabled = false,
            masterPasswordVerifier = ""
        )
        val cipher = SimpleShellPcCryptoCompat.createCipher(config)

        val payload = cipher.encryptText("hello")

        assertTrue(payload.startsWith("ssv2:"))
        assertEquals("hello", cipher.decryptText(payload))
    }
}
