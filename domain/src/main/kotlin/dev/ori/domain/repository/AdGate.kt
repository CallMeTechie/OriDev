package dev.ori.domain.repository

import dev.ori.domain.model.AdSlot

interface AdGate {
    suspend fun shouldShow(slot: AdSlot): Boolean
    suspend fun recordShown(slot: AdSlot)
    suspend fun recordDismissed(slot: AdSlot)
}
