package com.example.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.WatchHistoryEntry
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.axionSessionStore: DataStore<Preferences> by preferencesDataStore(
    name = "axion_stream_secure_prefs"
)

/**
 * Persists authenticated session tokens (AES-256-GCM encrypted via Android Keystore),
 * a privacy-safe app-scoped device identifier, and local playback resume timestamps.
 */
class SessionManager(private val context: Context) {

    @Volatile
    private var cachedToken: String? = null

    fun currentTokenSync(): String? = cachedToken

    suspend fun getOrCreateDeviceId(): String {
        val prefs = context.axionSessionStore.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = "axn-android-${UUID.randomUUID()}"
        context.axionSessionStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }

    suspend fun saveSessionToken(token: String) {
        cachedToken = token
        val encrypted = encryptToken(token)
        context.axionSessionStore.edit { prefs ->
            prefs[KEY_SESSION_TOKEN] = encrypted
        }
    }

    suspend fun loadSessionToken(): String? {
        cachedToken?.let { return it }
        val prefs = context.axionSessionStore.data.first()
        val stored = prefs[KEY_SESSION_TOKEN] ?: return null
        val decrypted = decryptToken(stored)
        cachedToken = decrypted
        return decrypted
    }

    suspend fun clearSession() {
        cachedToken = null
        context.axionSessionStore.edit { prefs ->
            prefs.remove(KEY_SESSION_TOKEN)
        }
    }

    suspend fun saveWatchPosition(movieId: String, positionMs: Long, durationMs: Long) {
        if (movieId.isBlank() || positionMs < 0L) return
        val key = stringPreferencesKey("resume_$movieId")
        val payload = "$positionMs:$durationMs"
        context.axionSessionStore.edit { prefs ->
            prefs[key] = payload
        }
    }

    suspend fun loadAllWatchHistory(): Map<String, WatchHistoryEntry> {
        val prefs = context.axionSessionStore.data.first()
        val map = mutableMapOf<String, WatchHistoryEntry>()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith("resume_") && value is String) {
                val movieId = name.removePrefix("resume_")
                val parts = value.split(":")
                val pos = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                if (pos > 0L) {
                    map[movieId] = WatchHistoryEntry(
                        movieId = movieId,
                        positionMs = pos,
                        durationMs = dur
                    )
                }
            }
        }
        return map
    }

    suspend fun clearWatchPosition(movieId: String) {
        val key = stringPreferencesKey("resume_$movieId")
        context.axionSessionStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    private fun getOrCreateAesKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrNull()
    }

    private fun encryptToken(plainText: String): String {
        val secretKey = getOrCreateAesKey() ?: return plainText
        return runCatching {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrDefault(plainText)
    }

    private fun decryptToken(stored: String): String? {
        if (!stored.startsWith(ENC_PREFIX)) return stored
        val secretKey = getOrCreateAesKey() ?: return null
        return runCatching {
            val combined = Base64.decode(stored.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return@runCatching null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "axion_stream_session_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val ENC_PREFIX = "enc_v1:"
        private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
