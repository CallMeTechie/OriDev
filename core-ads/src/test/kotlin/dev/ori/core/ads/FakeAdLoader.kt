package dev.ori.core.ads

import dev.ori.domain.model.AdSlot

class FakeAdLoader : AdLoader {
    var nextResult: AdLoadResult = AdLoadResult.NoFill
    override suspend fun loadBanner(slot: AdSlot) = nextResult
    override suspend fun loadNative(slot: AdSlot) = nextResult
    override fun destroy(slot: AdSlot) {
        // no-op for tests
    }
}
