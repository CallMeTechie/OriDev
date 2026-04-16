package dev.ori.core.ads

import dev.ori.domain.model.AdSlot

interface AdLoader {
    suspend fun loadBanner(slot: AdSlot): AdLoadResult
    suspend fun loadNative(slot: AdSlot): AdLoadResult
    fun destroy(slot: AdSlot)
}
