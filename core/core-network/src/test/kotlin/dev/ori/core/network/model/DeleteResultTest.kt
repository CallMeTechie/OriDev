package dev.ori.core.network.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DeleteResultTest {
    @Test fun isFullSuccess_emptyFailed_true() {
        assertThat(DeleteResult(listOf("/a"), emptyList()).isFullSuccess).isTrue()
    }

    @Test fun isFullSuccess_nonEmptyFailed_false() {
        assertThat(DeleteResult(listOf("/a"), listOf("/b" to "x")).isFullSuccess).isFalse()
    }

    @Test fun merge_combines() {
        val a = DeleteResult(listOf("/x"), listOf("/y" to "e"))
        val b = DeleteResult(listOf("/z"), listOf("/w" to "f"))
        val m = a.merge(b)
        assertThat(m.succeeded).containsExactly("/x", "/z").inOrder()
        assertThat(m.failed).containsExactly("/y" to "e", "/w" to "f").inOrder()
    }
}
