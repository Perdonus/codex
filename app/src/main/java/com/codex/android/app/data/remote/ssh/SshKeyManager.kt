package com.codex.android.app.data.remote.ssh

import android.content.Context
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class SshKeyManager(
    private val context: Context,
) {
    fun generateKeyPair(accountId: String, comment: String): GeneratedSshKeyPair {
        val existingPrivatePem = loadPrivateKey(accountId)
        val existingPublicKey = loadPublicKey(accountId)
        if (existingPrivatePem != null && existingPublicKey != null) {
            return GeneratedSshKeyPair(privatePem = existingPrivatePem, publicOpenSsh = existingPublicKey)
        }
        val keyPair = createPemKeyPair()
        val privatePem = encodePrivateKeyPem(keyPair.private.encoded)
        val publicOpenSsh = encodePublicKey(keyPair.public.encoded, comment)
        encryptedPrivateKeyFile(accountId).apply {
            parentFile?.mkdirs()
            writeText(privatePem)
        }
        publicKeyFile(accountId).apply {
            parentFile?.mkdirs()
            writeText(publicOpenSsh)
        }
        return GeneratedSshKeyPair(privatePem = privatePem, publicOpenSsh = publicOpenSsh)
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
        return encryptedPrivateKeyFile(accountId).exists() && publicKeyFile(accountId).exists()
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
        writeBytes(sshMpInt(exponent))
        writeBytes(sshMpInt(modulus))
        return buffer.toByteArray()
    }

    private fun sshMpInt(value: BigInteger): ByteArray {
        // BigInteger already preserves the leading zero byte when SSH mpint needs it.
        return value.toByteArray()
    }

    private fun createPemKeyPair(): java.security.KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(4096)
        return generator.generateKeyPair()
    }

    private fun encodePrivateKeyPem(encoded: ByteArray): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            appendLine(base64)
            appendLine("-----END PRIVATE KEY-----")
        }
    }
}

data class GeneratedSshKeyPair(
    val privatePem: String? = null,
    val publicOpenSsh: String,
)
