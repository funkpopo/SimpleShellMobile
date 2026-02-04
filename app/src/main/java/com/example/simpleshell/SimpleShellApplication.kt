package com.example.simpleshell

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class SimpleShellApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // sshj negotiates modern key exchange algorithms (e.g., curve25519/X25519). Android ships a
        // stripped/older "BC" provider under the same provider name which often lacks these.
        // Replace the platform provider with the bundled BouncyCastle (from sshj transitive deps).
        try {
            val providerName = BouncyCastleProvider.PROVIDER_NAME // "BC"
            val existing = Security.getProvider(providerName)
            val isBundledBc = existing?.javaClass?.name == "org.bouncycastle.jce.provider.BouncyCastleProvider"
            if (!isBundledBc) {
                // Preserve provider ordering as much as possible to avoid surprising changes in
                // unrelated crypto code paths that rely on default provider selection.
                val existingPos = Security.getProviders().indexOf(existing)
                val targetPos = if (existingPos >= 0) existingPos + 1 else 1

                Security.removeProvider(providerName)
                val insertedPos = Security.insertProviderAt(BouncyCastleProvider(), targetPos)
                if (insertedPos == -1) {
                    Security.addProvider(BouncyCastleProvider())
                }
            }
        } catch (t: Throwable) {
            // Don't crash on startup; we'll surface the underlying connection error later.
            Log.w("SimpleShell", "Failed to install BouncyCastle provider", t)
        }
    }
}
