package dev.ori.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class TabMemoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `round-trip preserves all fields`() {
        val memo = TabMemo(profileId = 42L, tabCount = 3, focusedWithinProfile = 1)
        val encoded = json.encodeToString(TabMemo.serializer(), memo)
        val decoded = json.decodeFromString(TabMemo.serializer(), encoded)
        assertThat(decoded).isEqualTo(memo)
    }

    @Test
    fun `decoder tolerates unknown keys`() {
        val raw = """{"profileId":1,"tabCount":2,"focusedWithinProfile":0,"future":"x"}"""
        val decoded = json.decodeFromString(TabMemo.serializer(), raw)
        assertThat(decoded).isEqualTo(TabMemo(1L, 2, 0))
    }
}
