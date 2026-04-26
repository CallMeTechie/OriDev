package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NameCacheTest {
    @Test fun resolveUid_idInCache_returnsName() {
        val cache = NameCache(uids = mapOf(1000 to "marc"), gids = emptyMap())
        assertThat(cache.resolveUid(1000)).isEqualTo("marc")
    }
    @Test fun resolveUid_idMissing_returnsNumericString() {
        assertThat(NameCache.empty().resolveUid(1000)).isEqualTo("1000")
    }
    @Test fun resolveGid_idInCache_returnsName() {
        val cache = NameCache(uids = emptyMap(), gids = mapOf(50 to "staff"))
        assertThat(cache.resolveGid(50)).isEqualTo("staff")
    }
    @Test fun empty_resolvesToNumeric() {
        val c = NameCache.empty()
        assertThat(c.resolveUid(1)).isEqualTo("1")
        assertThat(c.resolveGid(1)).isEqualTo("1")
    }
}
