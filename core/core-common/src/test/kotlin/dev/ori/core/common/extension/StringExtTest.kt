package dev.ori.core.common.extension

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StringExtTest {

    @Test
    fun validIp_isValidHost() {
        assertThat("192.168.1.100".isValidHost()).isTrue()
    }

    @Test
    fun validHostname_isValidHost() {
        assertThat("server01.example.com".isValidHost()).isTrue()
    }

    @Test
    fun localhost_isValidHost() {
        assertThat("pve1.local".isValidHost()).isTrue()
    }

    @Test
    fun empty_isNotValidHost() {
        assertThat("".isValidHost()).isFalse()
    }

    @Test
    fun specialChars_isNotValidHost() {
        assertThat("server;rm -rf".isValidHost()).isFalse()
    }

    @Test
    fun validPort_isValid() {
        assertThat("22".isValidPort()).isTrue()
        assertThat("8006".isValidPort()).isTrue()
    }

    @Test
    fun zeroPort_isNotValid() {
        assertThat("0".isValidPort()).isFalse()
    }

    @Test
    fun portAbove65535_isNotValid() {
        assertThat("70000".isValidPort()).isFalse()
    }

    @Test
    fun nonNumericPort_isNotValid() {
        assertThat("abc".isValidPort()).isFalse()
    }

    @Test
    fun truncateMiddle_shortString_unchanged() {
        assertThat("hello".truncateMiddle(10)).isEqualTo("hello")
    }

    @Test
    fun truncateMiddle_longString_truncated() {
        val result = "/very/long/path/to/some/file.txt".truncateMiddle(20)
        assertThat(result.length).isAtMost(20)
        assertThat(result).contains("...")
    }
}
