package dev.ori.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TransferStatusTest {

    @Test
    fun completed_isTerminal() {
        assertThat(TransferStatus.COMPLETED.isTerminal).isTrue()
    }

    @Test
    fun failed_isTerminal() {
        assertThat(TransferStatus.FAILED.isTerminal).isTrue()
    }

    @Test
    fun active_isNotTerminal() {
        assertThat(TransferStatus.ACTIVE.isTerminal).isFalse()
    }

    @Test
    fun queued_isActive() {
        assertThat(TransferStatus.QUEUED.isActive).isTrue()
    }

    @Test
    fun completed_isNotActive() {
        assertThat(TransferStatus.COMPLETED.isActive).isFalse()
    }
}
