package dev.ori.feature.editor.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiffDataHolderTest {

    @BeforeEach
    fun setup() {
        DiffDataHolder.clear()
    }

    @AfterEach
    fun tearDown() {
        DiffDataHolder.clear()
    }

    @Test
    fun `put then get returns payload and does not remove`() {
        val payload = DiffPayload("old", "new", "a.txt", "a.txt")
        DiffDataHolder.put("id1", payload)

        assertThat(DiffDataHolder.get("id1")).isEqualTo(payload)
        // get() must NOT remove -- survives rotation
        assertThat(DiffDataHolder.get("id1")).isEqualTo(payload)
    }

    @Test
    fun `remove clears payload`() {
        val payload = DiffPayload("old", "new", "a.txt", "a.txt")
        DiffDataHolder.put("id1", payload)
        DiffDataHolder.remove("id1")
        assertThat(DiffDataHolder.get("id1")).isNull()
    }

    @Test
    fun `expired entries are pruned on access`() {
        val expired = DiffPayload(
            oldContent = "old",
            newContent = "new",
            oldTitle = "a",
            newTitle = "a",
            createdAt = System.currentTimeMillis() - (11 * 60 * 1000L),
        )
        DiffDataHolder.put("expired", expired)

        // A subsequent put() triggers pruning.
        DiffDataHolder.put("fresh", DiffPayload("o", "n", "b", "b"))

        assertThat(DiffDataHolder.get("expired")).isNull()
        assertThat(DiffDataHolder.get("fresh")).isNotNull()
    }

    @Test
    fun `get survives multiple reads simulating rotation`() {
        val payload = DiffPayload("a", "b", "x", "y")
        DiffDataHolder.put("k", payload)
        repeat(5) {
            assertThat(DiffDataHolder.get("k")).isEqualTo(payload)
        }
    }
}
