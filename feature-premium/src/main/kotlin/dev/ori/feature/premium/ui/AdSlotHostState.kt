package dev.ori.feature.premium.ui

sealed class AdSlotHostState {
    data object Hidden : AdSlotHostState()
    data object Loading : AdSlotHostState()
    data class Banner(val handle: Any) : AdSlotHostState()
    data class Native(val handle: Any) : AdSlotHostState()
    data object House : AdSlotHostState()
}
