package com.example.data.video

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.studioBackendDataStore by preferencesDataStore(name = "owner_secure_backend_prefs")

data class OwnerBackendConfig(
    val backendBaseUrl: String = "",
    val apiKey: String = "",
    val videoModelId: String = "veo-3.0-generate-preview",
    val authHeaderName: String = "Authorization",
    val usesBearerPrefix: Boolean = true,
    val fromBuildSecrets: Boolean = false
) {
    val isConfigured: Boolean
        get() = backendBaseUrl.isNotBlank() && apiKey.isNotBlank()
}

/**
 * Encrypts the owner's Video Generation Backend API Key using Android Keystore (AES-256-GCM)
 * before storing it locally in DataStore Preferences.
 */
class SecureBackendConfigStore(private val context: Context) {

    private val keyUrl = stringPreferencesKey("backend_base_url")
    private val keyEncryptedApiKey = stringPreferencesKey("encrypted_api_key_b64")
    private val keyModelId = stringPreferencesKey("video_model_id")
    private val keyAuthHeader = stringPreferencesKey("auth_header_name")
    private val keyBearerPrefix = stringPreferencesKey("uses_bearer_prefix")

    val configFlow: Flow<OwnerBackendConfig> = context.studioBackendDataStore.data.map { prefs ->
        val storedUrl = prefs[keyUrl].orEmpty()
        val encryptedB64 = prefs[keyEncryptedApiKey].orEmpty()
        val decryptedKey = if (encryptedB64.isNotBlank()) decryptString(encryptedB64) else ""

        val envUrl = BuildConfig.VIDEO_BACKEND_URL.trim()
            .takeUnless { it == "YOUR_VIDEO_BACKEND_URL" }.orEmpty()
        val envKey = BuildConfig.VIDEO_BACKEND_API_KEY.trim()
            .takeUnless { it == "YOUR_VIDEO_BACKEND_API_KEY" }.orEmpty()

        val effectiveUrl = storedUrl.ifBlank { envUrl }
        val effectiveKey = decryptedKey.ifBlank { envKey }
        val usingSecrets = storedUrl.isBlank() && decryptedKey.isBlank() && envUrl.isNotBlank()

        OwnerBackendConfig(
            backendBaseUrl = effectiveUrl,
            apiKey = effectiveKey,
            videoModelId = prefs[keyModelId]?.ifBlank { null } ?: "veo-3.0-generate-preview",
            authHeaderName = prefs[keyAuthHeader]?.ifBlank { null } ?: "Authorization",
            usesBearerPrefix = prefs[keyBearerPrefix]?.toBooleanStrictOrNull() ?: true,
            fromBuildSecrets = usingSecrets
        )
    }

    suspend fun getConfigOnce(): OwnerBackendConfig = configFlow.first()

    suspend fun saveConfig(
        backendBaseUrl: String,
        apiKey: String,
        videoModelId: String,
        authHeaderName: String,
        usesBearerPrefix: Boolean
    ) {
        val cleanUrl = backendBaseUrl.trim().trimEnd('/')
        val cleanKey = apiKey.trim()
        val encrypted = if (cleanKey.isNotBlank()) encryptString(cleanKey) else ""

        context.studioBackendDataStore.edit { prefs ->
            prefs[keyUrl] = cleanUrl
            prefs[keyEncryptedApiKey] = encrypted
            prefs[keyModelId] = videoModelId.trim().ifBlank { "veo-3.0-generate-preview" }
            prefs[keyAuthHeader] = authHeaderName.trim().ifBlank { "Authorization" }
            prefs[keyBearerPrefix] = usesBearerPrefix.toString()
        }
    }

    suspend fun clearConfig() {
        context.studioBackendDataStore.edit { it.clear() }
    }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encryptString(plainText: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + encryptedBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, 1 + iv.size, encryptedBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptString(encoded: String): String {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLength)
            val cipherText = combined.copyOfRange(1 + ivLength, combined.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ashish_studio_owner_backend_key"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
