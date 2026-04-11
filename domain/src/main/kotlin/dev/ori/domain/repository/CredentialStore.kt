package dev.ori.domain.repository

interface CredentialStore {
    suspend fun storePassword(alias: String, password: CharArray)
    suspend fun getPassword(alias: String): CharArray?
    suspend fun storeSshKey(alias: String, privateKey: ByteArray)
    suspend fun getSshKey(alias: String): ByteArray?
    suspend fun deleteCredential(alias: String)
    suspend fun hasCredential(alias: String): Boolean
}
