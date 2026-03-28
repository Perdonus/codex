package com.codex.android.app.core.util

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

object SecurityProviderCompat {
    fun installFullBouncyCastle() {
        val currentProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (currentProvider?.javaClass == BouncyCastleProvider::class.java) return

        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
