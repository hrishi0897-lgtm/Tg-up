package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages sensitive Telegram credentials (Bot Token, Chat ID) encrypted at rest
 * using hardware-backed Android KeyStore AES-256-GCM.
 * Never stores plain text tokens.
 */
class EncryptedCredentialsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "televault_secure_prefs"
        private const val KEY_ALIAS = "TeleVaultKeyAlias"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private const val PREF_TOKEN = "encrypted_bot_token"
        private const val PREF_CHAT_ID = "encrypted_chat_id"
        private const val PREF_CHUNK_SIZE_MB = "chunk_size_mb"
        private const val DEFAULT_CHUNK_SIZE_MB = 45
    }

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Key not found in Android KeyStore")
        return entry.secretKey
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // Combine IV + encrypted bytes
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return null
            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decrypted = cipher.doFinal(cipherText)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCredentials(botToken: String, chatId: String) {
        val encryptedToken = encrypt(botToken.trim())
        val encryptedChatId = encrypt(chatId.trim())
        prefs.edit()
            .putString(PREF_TOKEN, encryptedToken)
            .putString(PREF_CHAT_ID, encryptedChatId)
            .apply()
    }

    fun getBotToken(): String? {
        val encrypted = prefs.getString(PREF_TOKEN, null) ?: return null
        return decrypt(encrypted)
    }

    fun getChatId(): String? {
        val encrypted = prefs.getString(PREF_CHAT_ID, null) ?: return null
        return decrypt(encrypted)
    }

    fun hasCredentials(): Boolean {
        val token = getBotToken()
        val chatId = getChatId()
        return !token.isNullOrBlank() && !chatId.isNullOrBlank()
    }

    fun clearCredentials() {
        prefs.edit().remove(PREF_TOKEN).remove(PREF_CHAT_ID).apply()
    }

    fun getChunkSizeMb(): Int {
        return prefs.getInt(PREF_CHUNK_SIZE_MB, DEFAULT_CHUNK_SIZE_MB)
    }

    fun setChunkSizeMb(sizeMb: Int) {
        val clamped = sizeMb.coerceIn(5, 48) // Safe margin below Telegram 50MB Bot limit
        prefs.edit().putInt(PREF_CHUNK_SIZE_MB, clamped).apply()
    }
}
