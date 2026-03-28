package com.codex.android.app.core.util

import net.schmizz.sshj.common.SecurityUtils

object SecurityProviderCompat {
    fun configureAndroidSshProviders() {
        // On Android, sshj's BC-forced path breaks modern KEX such as X25519/ECDH.
        // Keep sshj on the platform providers so it can negotiate with modern OpenSSH servers.
        SecurityUtils.setSecurityProvider(null)
        SecurityUtils.setRegisterBouncyCastle(false)
    }
}
