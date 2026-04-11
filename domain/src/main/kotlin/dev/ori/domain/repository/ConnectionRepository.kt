package dev.ori.domain.repository

import dev.ori.domain.model.Connection
import dev.ori.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun getAllProfiles(): Flow<List<ServerProfile>>
    fun getFavoriteProfiles(): Flow<List<ServerProfile>>
    suspend fun getProfileById(id: Long): ServerProfile?
    suspend fun getProfileCount(): Int
    suspend fun saveProfile(profile: ServerProfile): Long
    suspend fun updateProfile(profile: ServerProfile)
    suspend fun deleteProfile(profile: ServerProfile)
    suspend fun connect(profileId: Long): Connection
    suspend fun disconnect(profileId: Long)
    fun getActiveConnections(): Flow<List<Connection>>
}
