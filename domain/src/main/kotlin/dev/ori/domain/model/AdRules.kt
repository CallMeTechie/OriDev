package dev.ori.domain.model

data class AdRules(
    val maxBannersPerScreen: Int = 1,
    val houseAdDismissedForMs: Long = 7 * 24 * 60 * 60 * 1000L,
)
