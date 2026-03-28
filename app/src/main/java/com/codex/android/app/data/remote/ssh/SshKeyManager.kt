package com.codex.android.app.data.remote.ssh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class SshKeyManager(
    private val context: Context,
) {
    fun generateKeyPair(accountId: String, comment: String): GeneratedSshKeyPair {
        val alias = keyAlias(accountId)
        val keyPair = loadKeyPair(accountId) ?: createAndroidKeyStorePair(alias)
        val publicOpenSsh = encodePublicKey(keyPair.public.encoded, comment)
        publicKeyFile(accountId).apply {
            parentFile?.mkdirs()
            writeText(publicOpenSsh)
        }
        return GeneratedSshKeyPair(publicOpenSsh = publicOpenSsh, storedInAndroidKeyStore = true)
    }

    fun store(accountId: String, keyPair: GeneratedSshKeyPair) {
        if (keyPair.privatePem != null) {
            val privateFile = encryptedPrivateKeyFile(accountId)
            privateFile.parentFile?.mkdirs()
            privateFile.writeText(keyPair.privatePem)
        }
        publicKeyFile(accountId).apply {
            parentFile?.mkdirs()
            writeText(keyPair.publicOpenSsh)
        }
    }

    fun hasKey(accountId: String): Boolean {
        return loadKeyPair(accountId) != null || encryptedPrivateKeyFile(accountId).exists()
    }

    fun loadKeyPair(accountId: String): KeyPair? {
        val keyStore = loadAndroidKeyStore()
        val alias = keyAlias(accountId)
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey ?: return null
        val publicKey = keyStore.getCertificate(alias)?.publicKey ?: return null
        return KeyPair(publicKey, privateKey)
    }

    fun loadPrivateKey(accountId: String): String? {
        val file = encryptedPrivateKeyFile(accountId)
        if (!file.exists()) return null
        return file.readText()
    }

    fun loadPublicKey(accountId: String): String? {
        return publicKeyFile(accountId).takeIf(File::exists)?.readText()
    }

    fun delete(accountId: String) {
        runCatching {
            val keyStore = loadAndroidKeyStore()
            if (keyStore.containsAlias(keyAlias(accountId))) {
                keyStore.deleteEntry(keyAlias(accountId))
            }
        }
        encryptedPrivateKeyFile(accountId).delete()
        publicKeyFile(accountId).delete()
    }

    private fun encryptedPrivateKeyFile(accountId: String) = File(context.filesDir, "keys/$accountId/private.pem")

    private fun publicKeyFile(accountId: String) = File(context.filesDir, "keys/$accountId/public.pub")

    private fun encodePublicKey(encoded: ByteArray, comment: String): String {
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(encoded)) as RSAPublicKey
        val sshPayload = buildSshRsaPayload(publicKey.publicExponent, publicKey.modulus)
        val base64 = Base64.getEncoder().encodeToString(sshPayload)
        return "ssh-rsa $base64 $comment"
    }

    private fun buildSshRsaPayload(exponent: BigInteger, modulus: BigInteger): ByteArray {
        val buffer = ArrayList<Byte>()
        fun writeBytes(bytes: ByteArray) {
            val length = bytes.size
            buffer.add(((length shr 24) and 0xFF).toByte())
            buffer.add(((length shr 16) and 0xFF).toByte())
            buffer.add(((length shr 8) and 0xFF).toByte())
            buffer.add((length and 0xFF).toByte())
            bytes.forEach(buffer::add)
        }
        writeBytes("ssh-rsa".toByteArray())
        writeBytes(stripLeadingZero(exponent.toByteArray()))
        writeBytes(stripLeadingZero(modulus.toByteArray()))
        return buffer.toByteArray()
    }

    private fun stripLeadingZero(bytes: ByteArray): ByteArray {
        return if (bytes.size > 1 && bytes.first() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun loadAndroidKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun createAndroidKeyStorePair(alias: String): KeyPair {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setKeySize(4096)
            .setDigests(
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512,
            )
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private fun keyAlias(accountId: String): String = "codex_ssh_$accountId"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

data class GeneratedSshKeyPair(
    val privatePem: String? = null,
    val publicOpenSsh: String,
    val storedInAndroidKeyStore: Boolean = false,
)
