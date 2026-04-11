package dev.ori.core.common.extension

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LongExtTest {

    @Test
    fun bytes_formatsAsBytes() {
        assertThat(512L.toHumanReadableSize()).isEqualTo("512 B")
    }

    @Test
    fun kilobytes_formatsAsKB() {
        assertThat(1024L.toHumanReadableSize()).isEqualTo("1 KB")
    }

    @Test
    fun megabytes_formatsAsMB() {
        assertThat((5L * 1024 * 1024).toHumanReadableSize()).isEqualTo("5 MB")
    }

    @Test
    fun gigabytes_formatsWithDecimals() {
        assertThat((1536L * 1024 * 1024).toHumanReadableSize()).isEqualTo("1.5 GB")
    }
}
