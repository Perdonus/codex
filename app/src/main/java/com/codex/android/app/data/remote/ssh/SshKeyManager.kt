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
    fun generateKeyPair(comment: String): GeneratedSshKeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(4096)
        val keyPair = generator.generateKeyPair()
        val privatePem = encodePrivatePem(keyPair.private.encoded)
        val publicOpenSsh = encodePublicKey(keyPair.public.encoded, comment)
        return GeneratedSshKeyPair(privatePem = privatePem, publicOpenSsh = publicOpenSsh)
    }

    fun store(accountId: String, keyPair: GeneratedSshKeyPair) {
        val privateFile = encryptedPrivateKeyFile(accountId)
        privateFile.parentFile?.mkdirs()
        privateFile.writeText(keyPair.privatePem)
        publicKeyFile(accountId).writeText(keyPair.publicOpenSsh)
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

    private fun encodePrivatePem(encoded: ByteArray): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            appendLine(base64)
            append("-----END PRIVATE KEY-----")
        }
    }

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
}

data class GeneratedSshKeyPair(
    val privatePem: String,
    val publicOpenSsh: String,
)
