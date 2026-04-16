package dev.ori.domain.model

@JvmInline
value class BandwidthLimit(val kbps: Int?) {
    val isUnlimited: Boolean get() = kbps == null || kbps == 0

    companion object {
        val UNLIMITED = BandwidthLimit(null)
        val PRESETS = listOf(64, 128, 256, 512, 1024, 2048, 5120, 10240)
    }
}
