package dev.ori.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 12 P12.5 catch-up — unit tests for [TransferNotificationText], the
 * pure-Kotlin text formatting helpers extracted from
 * [TransferNotificationManager]. Covers each binary unit boundary for
 * [TransferNotificationText.humanReadableBytes], the zero / singular / plural
 * branches of [TransferNotificationText.aggregateTitle], the mid-progress and
 * indeterminate branches of [TransferNotificationText.aggregateBody], and
 * the exact-half / zero-total / over-100 clamp branches of
 * [TransferNotificationText.progressPercent].
 */
class TransferNotificationTextTest {

    @Test
    fun humanReadableBytes_under1024_showsBytes() {
        assertThat(TransferNotificationText.humanReadableBytes(512)).isEqualTo("512 B")
    }

    @Test
    fun humanReadableBytes_kibibyte_showsKiB() {
        assertThat(TransferNotificationText.humanReadableBytes(2048)).isEqualTo("2.0 KiB")
    }

    @Test
    fun humanReadableBytes_mebibyte_showsMiB() {
        assertThat(
            TransferNotificationText.humanReadableBytes(5L * 1024 * 1024),
        ).isEqualTo("5.0 MiB")
    }

    @Test
    fun humanReadableBytes_gibibyte_showsGiB() {
        assertThat(
            TransferNotificationText.humanReadableBytes(2L * 1024 * 1024 * 1024),
        ).isEqualTo("2.0 GiB")
    }

    @Test
    fun aggregateTitle_zero_showsPreparing() {
        assertThat(TransferNotificationText.aggregateTitle(0)).isEqualTo("Preparing transfers")
    }

    @Test
    fun aggregateTitle_one_showsSingular() {
        assertThat(TransferNotificationText.aggregateTitle(1)).isEqualTo("1 active transfer")
    }

    @Test
    fun aggregateTitle_many_showsPlural() {
        assertThat(TransferNotificationText.aggregateTitle(3)).isEqualTo("3 active transfers")
    }

    @Test
    fun aggregateBody_midProgress_showsBytesAndPercent() {
        val body = TransferNotificationText.aggregateBody(512L * 1024, 1024L * 1024)
        assertThat(body).contains("/")
        assertThat(body).contains("50%")
        assertThat(body).contains("KiB")
        assertThat(body).contains("MiB")
    }

    @Test
    fun aggregateBody_zeroTotal_returnsIndeterminatePlaceholder() {
        assertThat(TransferNotificationText.aggregateBody(0, 0)).isEqualTo("—")
    }

    @Test
    fun aggregateBody_unknownTotalWithTransferred_returnsIndeterminatePlaceholder() {
        assertThat(TransferNotificationText.aggregateBody(500, 0)).isEqualTo("—")
    }

    @Test
    fun progressPercent_exactHalf_returns50() {
        assertThat(TransferNotificationText.progressPercent(500, 1000)).isEqualTo(50)
    }

    @Test
    fun progressPercent_zeroTotal_returns0() {
        assertThat(TransferNotificationText.progressPercent(0, 0)).isEqualTo(0)
    }

    @Test
    fun progressPercent_over100_capsAt100() {
        assertThat(TransferNotificationText.progressPercent(2000, 1000)).isEqualTo(100)
    }
}
