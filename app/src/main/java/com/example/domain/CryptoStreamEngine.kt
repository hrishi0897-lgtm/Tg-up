package com.example.domain

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-Knowledge Client-Side AES-256-GCM Chunk Encryption Engine.
 *
 * Each chunk is encrypted on-device prior to Telegram upload using a 256-bit per-file AES key
 * and a unique 12-byte GCM initialization vector (IV) prepended to the encrypted chunk payload.
 * The 128-bit GCM authentication tag is appended automatically at the end of the stream.
 */
object CryptoStreamEngine {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_BITS = 256
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val GCM_OVERHEAD_BYTES = 28 // 12-byte IV + 16-byte auth tag

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically strong 256-bit AES key encoded as Base64.
     */
    fun generateKeyBase64(): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_BITS, secureRandom)
        val secretKey = keyGen.generateKey()
        return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Returns an 8-character uppercase hex fingerprint of the AES-256 key for UI verification badges.
     */
    fun getKeyFingerprint(keyBase64: String?): String {
        if (keyBase64.isNullOrBlank()) return "NONE"
        return try {
            val raw = Base64.decode(keyBase64, Base64.NO_WRAP)
            val digest = MessageDigest.getInstance("SHA-256").digest(raw)
            digest.take(4).joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            "AES-256"
        }
    }

    private fun decodeSecretKey(keyBase64: String): SecretKey {
        val raw = Base64.decode(keyBase64.trim(), Base64.NO_WRAP)
        require(raw.size == 32) { "Invalid AES-256 key length: expected 32 bytes, got ${raw.size}" }
        return SecretKeySpec(raw, "AES")
    }

    /**
     * Encrypts [plainChunkFile] into [encryptedOutFile] using AES-256-GCM.
     * Prepends the 12-byte IV to [encryptedOutFile] and returns the SHA-256 hex checksum
     * of the resulting encrypted chunk file.
     */
    fun encryptChunkFile(
        plainChunkFile: File,
        encryptedOutFile: File,
        keyBase64: String,
        chunkIndex: Int = 0
    ): String {
        val secretKey = decodeSecretKey(keyBase64)
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)
        // Mix chunkIndex into the last 4 bytes of IV to guarantee per-chunk uniqueness
        iv[8] = (iv[8].toInt() xor ((chunkIndex ushr 24) and 0xFF)).toByte()
        iv[9] = (iv[9].toInt() xor ((chunkIndex ushr 16) and 0xFF)).toByte()
        iv[10] = (iv[10].toInt() xor ((chunkIndex ushr 8) and 0xFF)).toByte()
        iv[11] = (iv[11].toInt() xor (chunkIndex and 0xFF)).toByte()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

        FileOutputStream(encryptedOutFile).use { fos ->
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                FileInputStream(plainChunkFile).use { fis ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        cos.write(buffer, 0, read)
                    }
                }
            }
        }

        return ChecksumUtil.computeSha256(encryptedOutFile)
    }

    /**
     * Decrypts [encryptedChunkFile] into [plainOutFile] using AES-256-GCM.
     * Reads the 12-byte IV from the start of [encryptedChunkFile] and verifies the 128-bit GCM tag.
     */
    fun decryptChunkFile(
        encryptedChunkFile: File,
        plainOutFile: File,
        keyBase64: String
    ) {
        val secretKey = decodeSecretKey(keyBase64)
        FileInputStream(encryptedChunkFile).use { fis ->
            val iv = ByteArray(GCM_IV_BYTES)
            var totalIvRead = 0
            while (totalIvRead < GCM_IV_BYTES) {
                val r = fis.read(iv, totalIvRead, GCM_IV_BYTES - totalIvRead)
                if (r == -1) throw IllegalStateException("Encrypted chunk file is truncated (missing GCM IV)")
                totalIvRead += r
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(plainOutFile).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (cis.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted chunk entirely in RAM without touching disk storage.
     * Extracts the 12-byte IV prefix and verifies the 128-bit GCM authentication tag.
     */
    fun decryptChunkBytes(
        encryptedBytes: ByteArray,
        keyBase64: String
    ): ByteArray {
        require(encryptedBytes.size >= GCM_OVERHEAD_BYTES) {
            "Encrypted chunk payload is truncated (${encryptedBytes.size} bytes < $GCM_OVERHEAD_BYTES bytes)"
        }
        val secretKey = decodeSecretKey(keyBase64)
        val iv = encryptedBytes.copyOfRange(0, GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encryptedBytes, GCM_IV_BYTES, encryptedBytes.size - GCM_IV_BYTES)
    }
}
