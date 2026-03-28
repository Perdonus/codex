package com.codex.android.app.core.util

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.Security
import java.security.Signature
import javax.crypto.KeyAgreement
import net.schmizz.sshj.common.SecurityUtils

object SecurityProviderCompat {
    fun configureAndroidSshProviders() {
        SecurityUtils.setSecurityProvider(resolveSshProviderName())
        SecurityUtils.setRegisterBouncyCastle(false)
    }

    fun currentSshProviderName(): String = resolveSshProviderName() ?: "system-default"

    private fun resolveSshProviderName(): String? {
        val providers = Security.getProviders().toList()
        return providers.firstOrNull { provider ->
            provider.name !in blockedProviders && supportsX25519(provider)
        }?.name
    }

    private fun supportsX25519(provider: Provider): Boolean {
        return runCatching {
            KeyPairGenerator.getInstance("X25519", provider)
            KeyAgreement.getInstance("X25519", provider)
            KeyFactory.getInstance("X25519", provider)
            Signature.getInstance("SHA256withRSA", provider)
            true
        }.getOrElse {
            false
        }
    }

    private val blockedProviders = setOf(
        "AndroidKeyStore",
        "AndroidKeyStoreBCWorkaround",
    )
}
