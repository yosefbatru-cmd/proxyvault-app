package com.spiritdev.proxyvault.crypto

import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

object MrCrypto {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private const val VERSION: Byte = 0x02
    private const val MAGIC = "PROXYVAULT_MR_V2"
    private const val SCRYPT_N = 1 shl 18
    private const val SCRYPT_R = 8
    private const val SCRYPT_P = 1
    private const val KEY_LEN = 32
    private const val SALT_LEN = 32
    private const val NONCE_LEN = 12
    private const val HMAC_LEN = 32

    private val secureRandom = SecureRandom()

    fun export(password: CharArray, plaintext: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        val derived = scrypt(password, salt)

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        val kp = kpg.generateKeyPair()
        val pub = kp.public as RSAPublicKey

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(
            Cipher.ENCRYPT_MODE, pub,
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        )
        val wrappedKey = rsaCipher.doFinal(derived)

        val nonce = ByteArray(NONCE_LEN).also { secureRandom.nextBytes(it) }
        val aesKey = SecretKeySpec(derived, "AES")
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, nonce))

        val magicPayload = MAGIC.toByteArray(Charsets.UTF_8) + plaintext
        val ciphertext = gcm.doFinal(magicPayload)
        val pubEncoded = pub.encoded

        val body = ByteBuffer.allocate(
            32 + 1 + 1 + 2 + 4 + 4 + pubEncoded.size + 4 + wrappedKey.size + NONCE_LEN + 4 + ciphertext.size
        )
        body.put(salt)
        body.put(VERSION)
        body.put(0x00)
        body.putShort(0x0001)
        body.putInt(ciphertext.size)
        body.putInt(pubEncoded.size)
        body.put(pubEncoded)
        body.putInt(wrappedKey.size)
        body.put(wrappedKey)
        body.put(nonce)
        body.putInt(ciphertext.size)
        body.put(ciphertext)

        val bodyBytes = body.array()
        val hmac = hmacSha256(derived, bodyBytes)
        derived.fill(0)

        val out = ByteArray(bodyBytes.size + HMAC_LEN)
        System.arraycopy(bodyBytes, 0, out, 0, bodyBytes.size)
        System.arraycopy(hmac, 0, out, bodyBytes.size, HMAC_LEN)
        return out
    }

    fun import(password: CharArray, file: ByteArray): ByteArray {
        require(file.size > 80) { "File too small" }
        val buf = ByteBuffer.wrap(file)
        val salt = ByteArray(SALT_LEN)
        buf.get(salt)
        val version = buf.get()
        require(version <= VERSION) { "Unsupported version: $version" }
        buf.get()
        buf.short
        buf.int
        val pubLen = buf.int
        val pubEnc = ByteArray(pubLen)
        buf.get(pubEnc)
        val wrapLen = buf.int
        val wrapped = ByteArray(wrapLen)
        buf.get(wrapped)
        val nonce = ByteArray(NONCE_LEN)
        buf.get(nonce)
        val cipherLen = buf.int
        val ciphertext = ByteArray(cipherLen)
        buf.get(ciphertext)

        val bodyLen = file.size - HMAC_LEN
        val body = file.copyOfRange(0, bodyLen)
        val storedHmac = file.copyOfRange(bodyLen, file.size)

        val derived = scrypt(password, salt)
        val computedHmac = hmacSha256(derived, body)
        if (!constantTimeEquals(storedHmac, computedHmac)) {
            derived.fill(0)
            throw SecurityException("HMAC mismatch — file tampered or corrupted")
        }

        val aesKey = SecretKeySpec(derived, "AES")
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, nonce))
        val plain = try {
            gcm.doFinal(ciphertext)
        } catch (e: Exception) {
            derived.fill(0)
            throw SecurityException("Decryption failed — wrong password or corrupted payload")
        }
        derived.fill(0)

        val magicBytes = MAGIC.toByteArray(Charsets.UTF_8)
        if (plain.size < magicBytes.size || !plain.copyOfRange(0, magicBytes.size).contentEquals(magicBytes)) {
            throw SecurityException("Magic check failed — wrong password")
        }
        return plain.copyOfRange(magicBytes.size, plain.size)
    }

    private fun scrypt(password: CharArray, salt: ByteArray): ByteArray {
        val pw = password.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            SCrypt.generate(pw, salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, KEY_LEN)
        } finally {
            pw.fill(0)
        }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }
}
