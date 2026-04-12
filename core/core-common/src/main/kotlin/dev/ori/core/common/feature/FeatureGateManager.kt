package dev.ori.core.common.feature

interface FeatureGateManager {
    fun isPremium(): Boolean
    fun isFeatureEnabled(feature: PremiumFeature): Boolean
    fun maxServerProfiles(): Int
    fun maxTerminalTabs(): Int
    fun maxParallelTransfers(): Int
}

enum class PremiumFeature {
    SESSION_RECORDER,
    SEND_TO_CLAUDE,
    CODE_EDITOR_WRITE,
    FILE_WATCHER,
    BIOMETRIC_UNLOCK,
    CUSTOM_THEMES,
}

class FeatureGateManagerStub : FeatureGateManager {
    override fun isPremium(): Boolean = true
    override fun isFeatureEnabled(feature: PremiumFeature): Boolean = true
    override fun maxServerProfiles(): Int = Int.MAX_VALUE
    override fun maxTerminalTabs(): Int = Int.MAX_VALUE
    override fun maxParallelTransfers(): Int = Int.MAX_VALUE
}
