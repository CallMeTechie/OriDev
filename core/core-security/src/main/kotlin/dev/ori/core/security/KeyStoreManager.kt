package dev.ori.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.domain.repository.CredentialStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "oridev_credentials",
)

@Singleton
class KeyStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : CredentialStore {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override suspend fun storePassword(alias: String, password: CharArray) {
        val plaintext = String(password).toByteArray(Charsets.UTF_8)
        try {
            val encrypted = encryptWithMasterKey(plaintext)
            val base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            context.credentialDataStore.edit { prefs ->
                prefs[credentialKey(alias)] = base64
            }
        } finally {
            plaintext.fill(0)
            password.fill('\u0000')
        }
    }

    override suspend fun getPassword(alias: String): CharArray? {
        val base64 = readCredential(alias) ?: return null
        val encrypted = Base64.decode(base64, Base64.NO_WRAP)
        val decrypted = decryptWithMasterKey(encrypted)
        return try {
            String(decrypted, Charsets.UTF_8).toCharArray()
        } finally {
            decrypted.fill(0)
        }
    }

    override suspend fun storeSshKey(alias: String, privateKey: ByteArray) {
        val encrypted = encryptWithMasterKey(privateKey)
        val base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        context.credentialDataStore.edit { prefs ->
            prefs[credentialKey(alias)] = base64
        }
    }

    override suspend fun getSshKey(alias: String): ByteArray? {
        val base64 = readCredential(alias) ?: return null
        val encrypted = Base64.decode(base64, Base64.NO_WRAP)
        return decryptWithMasterKey(encrypted)
    }

    override suspend fun deleteCredential(alias: String) {
        context.credentialDataStore.edit { prefs ->
            prefs.remove(credentialKey(alias))
        }
    }

    override suspend fun hasCredential(alias: String): Boolean {
        val key = credentialKey(alias)
        return context.credentialDataStore.data
            .map { prefs -> prefs.contains(key) }
            .first()
    }

    private suspend fun readCredential(alias: String): String? {
        val key = credentialKey(alias)
        return context.credentialDataStore.data
            .map { prefs -> prefs[key] }
            .first()
    }

    private fun credentialKey(alias: String) = stringPreferencesKey("credential_$alias")

    private fun getOrCreateMasterKey(): SecretKey {
        val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun encryptWithMasterKey(plaintext: ByteArray): ByteArray {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    private fun decryptWithMasterKey(data: ByteArray): ByteArray {
        val key = getOrCreateMasterKey()
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "oridev_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
