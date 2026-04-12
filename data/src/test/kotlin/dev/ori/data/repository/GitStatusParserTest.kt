package dev.ori.data.repository

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.process.ProcessRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for [GitStatusParser.parseLineDiff] using a fake [ProcessRunner] that
 * returns canned git output so no real git binary is required.
 */
class GitStatusParserTest {

    private class FakeProcessRunner(
        private val responses: MutableMap<List<String>, String?> = mutableMapOf(),
    ) : ProcessRunner {
        fun stub(command: List<String>, output: String?) {
            responses[command] = output
        }

        override suspend fun run(
            command: List<String>,
            workingDir: String?,
            timeoutSeconds: Long,
        ): String? {
            // Match by the leading command tokens only so callers don't need to
            // know the absolute working directory.
            return responses.entries.firstOrNull { it.key == command }?.value
        }
    }

    private fun newTempGitRepo(): File {
        val dir = Files.createTempDirectory("oridev-gitparser-").toFile()
        File(dir, ".git").mkdir()
        return dir
    }

    @Test
    fun `parseLineDiff no changes returns empty`() = runTest {
        val repo = newTempGitRepo()
        val file = File(repo, "a.txt").apply { writeText("x") }
        val runner = FakeProcessRunner()
        runner.stub(listOf("git", "status", "--porcelain", "--", "a.txt"), "")
        runner.stub(listOf("git", "diff", "--unified=0", "HEAD", "--", "a.txt"), "")

        val parser = GitStatusParser(runner)
        val diff = parser.parseLineDiff(file)

        assertThat(diff).isEmpty()
    }

    @Test
    fun `parseLineDiff one added line returns added`() = runTest {
        val repo = newTempGitRepo()
        val file = File(repo, "a.txt").apply { writeText("hello\nworld\n") }
        val runner = FakeProcessRunner()
        runner.stub(listOf("git", "status", "--porcelain", "--", "a.txt"), " M a.txt")
        runner.stub(
            listOf("git", "diff", "--unified=0", "HEAD", "--", "a.txt"),
            """
            diff --git a/a.txt b/a.txt
            index 0000000..0000001 100644
            --- a/a.txt
            +++ b/a.txt
            @@ -1,0 +2,1 @@
            +world
            """.trimIndent(),
        )

        val parser = GitStatusParser(runner)
        val diff = parser.parseLineDiff(file)

        assertThat(diff).containsExactly(2, LineChangeType.ADDED)
    }

    @Test
    fun `parseLineDiff modified line returns modified`() = runTest {
        val repo = newTempGitRepo()
        val file = File(repo, "a.txt").apply { writeText("NEW\n") }
        val runner = FakeProcessRunner()
        runner.stub(listOf("git", "status", "--porcelain", "--", "a.txt"), " M a.txt")
        runner.stub(
            listOf("git", "diff", "--unified=0", "HEAD", "--", "a.txt"),
            """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1 +1 @@
            -old
            +NEW
            """.trimIndent(),
        )

        val parser = GitStatusParser(runner)
        val diff = parser.parseLineDiff(file)

        assertThat(diff).containsExactly(1, LineChangeType.MODIFIED)
    }

    @Test
    fun `parseLineDiff untracked file returns all added`() = runTest {
        val repo = newTempGitRepo()
        val file = File(repo, "new.txt").apply { writeText("one\ntwo\nthree\n") }
        val runner = FakeProcessRunner()
        runner.stub(listOf("git", "status", "--porcelain", "--", "new.txt"), "?? new.txt")

        val parser = GitStatusParser(runner)
        val diff = parser.parseLineDiff(file)

        assertThat(diff).hasSize(3)
        assertThat(diff.values.toSet()).containsExactly(LineChangeType.ADDED)
    }

    @Test
    fun `parseLineDiff process timeout returns empty`() = runTest {
        val repo = newTempGitRepo()
        val file = File(repo, "a.txt").apply { writeText("x") }
        val runner = FakeProcessRunner()
        // no stubs -> FakeProcessRunner.run returns null, simulating timeout/failure
        val parser = GitStatusParser(runner)

        val diff = parser.parseLineDiff(file)

        assertThat(diff).isEmpty()
    }

    @Test
    fun `parseLineDiff not a git repo returns empty`() = runTest {
        val dir = Files.createTempDirectory("oridev-nogit-").toFile()
        val file = File(dir, "a.txt").apply { writeText("x") }
        val runner = FakeProcessRunner()

        val parser = GitStatusParser(runner)
        val diff = parser.parseLineDiff(file)

        assertThat(diff).isEmpty()
    }
}
