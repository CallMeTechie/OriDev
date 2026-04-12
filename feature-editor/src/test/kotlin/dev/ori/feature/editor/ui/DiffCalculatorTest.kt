package dev.ori.feature.editor.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DiffCalculatorTest {

    @Test
    fun `identical texts produce only context lines`() {
        val text = "line1\nline2\nline3"
        val result = DiffCalculator.computeDiff(text, text)
        assertThat(result).isNotEmpty()
        assertThat(result.all { it.type == DiffType.CONTEXT }).isTrue()
    }

    @Test
    fun `insertion produces added lines`() {
        val old = "line1\nline3"
        val new = "line1\nline2\nline3"
        val result = DiffCalculator.computeDiff(old, new)
        val added = result.filter { it.type == DiffType.ADDED }
        assertThat(added).hasSize(1)
        assertThat(added.first().content).isEqualTo("line2")
        assertThat(added.first().newLineNumber).isEqualTo(2)
        assertThat(added.first().oldLineNumber).isNull()
    }

    @Test
    fun `deletion produces removed lines`() {
        val old = "line1\nline2\nline3"
        val new = "line1\nline3"
        val result = DiffCalculator.computeDiff(old, new)
        val removed = result.filter { it.type == DiffType.REMOVED }
        assertThat(removed).hasSize(1)
        assertThat(removed.first().content).isEqualTo("line2")
        assertThat(removed.first().oldLineNumber).isEqualTo(2)
    }

    @Test
    fun `modification produces removed and added pair`() {
        val old = "alpha\nbeta\ngamma"
        val new = "alpha\nBETA\ngamma"
        val result = DiffCalculator.computeDiff(old, new)
        val removed = result.filter { it.type == DiffType.REMOVED }
        val added = result.filter { it.type == DiffType.ADDED }
        assertThat(removed).hasSize(1)
        assertThat(added).hasSize(1)
        assertThat(removed.first().content).isEqualTo("beta")
        assertThat(added.first().content).isEqualTo("BETA")
    }

    @Test
    fun `empty to content yields only added lines`() {
        val result = DiffCalculator.computeDiff("", "hello\nworld")
        val added = result.filter { it.type == DiffType.ADDED }
        // Empty string's lines() returns [""]; new content will be added
        assertThat(added.size).isAtLeast(1)
        assertThat(added.any { it.content == "hello" }).isTrue()
        assertThat(added.any { it.content == "world" }).isTrue()
    }

    @Test
    fun `multiple deltas are all captured`() {
        val old = "a\nb\nc\nd\ne"
        val new = "a\nB\nc\nD\ne"
        val result = DiffCalculator.computeDiff(old, new)
        val added = result.filter { it.type == DiffType.ADDED }
        val removed = result.filter { it.type == DiffType.REMOVED }
        assertThat(added.map { it.content }).containsExactly("B", "D")
        assertThat(removed.map { it.content }).containsExactly("b", "d")
    }
}
