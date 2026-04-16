package dev.ori.domain.repository

import kotlinx.coroutines.flow.Flow

interface PremiumRepository {
    val isPremium: Flow<Boolean>
    suspend fun refreshEntitlement()
    suspend fun cacheEntitlement(value: Boolean)
    suspend fun getCachedEntitlement(): Boolean
    suspend fun getLastRefreshedAt(): Long?
}
