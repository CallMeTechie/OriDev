package dev.ori.core.security

import dev.ori.domain.repository.CredentialStore
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyStoreManager @Inject constructor() : CredentialStore {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val encryptedStore = ConcurrentHashMap<String, ByteArray>()

    override suspend fun storePassword(alias: String, password: CharArray) {
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        try {
            encryptedStore[alias] = encrypt(alias, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    override suspend fun getPassword(alias: String): CharArray? {
        val encrypted = encryptedStore[alias] ?: return null
        val decrypted = decrypt(alias, encrypted)
        return try {
            String(decrypted, Charsets.UTF_8).toCharArray()
        } finally {
            decrypted.fill(0)
        }
    }

    override suspend fun storeSshKey(alias: String, privateKey: ByteArray) {
        encryptedStore[alias] = encrypt(alias, privateKey)
    }

    override suspend fun getSshKey(alias: String): ByteArray? {
        val encrypted = encryptedStore[alias] ?: return null
        return decrypt(alias, encrypted)
    }

    override suspend fun deleteCredential(alias: String) {
        encryptedStore.remove(alias)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    override suspend fun hasCredential(alias: String): Boolean {
        return encryptedStore.containsKey(alias)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val entry = keyStore.getEntry(alias, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    private fun decrypt(alias: String, data: ByteArray): ByteArray {
        val key = getOrCreateKey(alias)
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
