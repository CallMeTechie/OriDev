package dev.ori.data.repository

import dev.ori.core.common.process.ProcessRunner
import dev.ori.domain.model.GitStatus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to detect git status for files in a local directory.
 *
 * Runs git commands via [ProcessRunner] (which uses ProcessBuilder under the
 * hood). Only works for local files -- remote (SFTP) files will not have git
 * status. If git is not installed or the directory is not a git repo, returns
 * an empty map.
 */
@Singleton
class GitStatusParser @Inject constructor(
    private val processRunner: ProcessRunner,
) {

    /**
     * Finds the git repository root for the given directory, if any.
     * Walks up the directory tree looking for a `.git` directory.
     */
    fun findGitRoot(directory: File?): File? {
        var current: File? = directory
        while (current != null) {
            if (File(current, ".git").exists()) return current
            current = current.parentFile
        }
        return null
    }

    /**
     * Parses `git status --porcelain` output for the given directory.
     *
     * @param directory The directory to check git status for.
     * @return A map of absolute file path to [GitStatus], or empty map if not a
     *         git repo or git is unavailable.
     */
    suspend fun parseStatus(directory: File): Map<String, GitStatus> {
        val gitRoot = findGitRoot(directory) ?: return emptyMap()
        val output = processRunner.run(
            command = listOf("git", "status", "--porcelain"),
            workingDir = gitRoot.absolutePath,
        ) ?: return emptyMap()
        return parsePortelainOutput(output, gitRoot)
    }

    /**
     * Parses unified-diff output (`git diff --unified=0 HEAD -- <file>`) to a
     * map of new-side line number -> change type.
     *
     * For untracked files (not in HEAD), all lines are marked as [LineChangeType.ADDED].
     */
    @Suppress("ReturnCount")
    suspend fun parseLineDiff(file: File): Map<Int, LineChangeType> {
        val root = findGitRoot(file.parentFile) ?: return emptyMap()
        val relativePath = file.relativeTo(root).path.ifEmpty { return emptyMap() }

        // Check if the file is untracked.
        val statusOutput = processRunner.run(
            command = listOf("git", "status", "--porcelain", "--", relativePath),
            workingDir = root.absolutePath,
        )
        if (statusOutput != null && statusOutput.lines().any { it.startsWith("??") }) {
            // Untracked file: mark every line as ADDED.
            if (!file.exists()) return emptyMap()
            val lineCount = runCatching { file.readLines().size }.getOrDefault(0)
            return (1..lineCount).associateWith { LineChangeType.ADDED }
        }

        val diffOutput = processRunner.run(
            command = listOf("git", "diff", "--unified=0", "HEAD", "--", relativePath),
            workingDir = root.absolutePath,
        ) ?: return emptyMap()

        return parseUnifiedDiff(diffOutput)
    }

    /**
     * Parses the porcelain output format.
     */
    @Suppress("CyclomaticComplexMethod")
    internal fun parsePortelainOutput(output: String, gitRoot: File): Map<String, GitStatus> {
        val result = mutableMapOf<String, GitStatus>()

        for (line in output.lines()) {
            if (line.length < 4) continue

            val indexStatus = line[0]
            val workTreeStatus = line[1]
            val relativePath = line.substring(3).trim()

            if (relativePath.isEmpty()) continue

            val effectivePath = if (relativePath.contains(" -> ")) {
                relativePath.substringAfter(" -> ")
            } else {
                relativePath
            }

            val absolutePath = File(gitRoot, effectivePath).absolutePath

            val status = when {
                indexStatus == '?' && workTreeStatus == '?' -> GitStatus.UNTRACKED
                indexStatus == 'A' -> GitStatus.STAGED
                indexStatus == 'M' && workTreeStatus == ' ' -> GitStatus.STAGED
                workTreeStatus == 'M' -> GitStatus.MODIFIED
                indexStatus == 'R' || indexStatus == 'C' -> GitStatus.STAGED
                indexStatus == 'D' || workTreeStatus == 'D' -> GitStatus.MODIFIED
                indexStatus != ' ' && indexStatus != '?' -> GitStatus.STAGED
                else -> continue
            }

            result[absolutePath] = status
        }

        return result
    }

    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod")
    internal fun parseUnifiedDiff(diff: String): Map<Int, LineChangeType> {
        val result = mutableMapOf<Int, LineChangeType>()
        val hunkRegex = Regex("""@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@""")

        val lines = diff.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val match = hunkRegex.find(line)
            if (match == null) {
                i++
                continue
            }
            val newStart = match.groupValues[1].toInt()
            var lineNo = newStart
            var hasRemoved = false
            i++
            while (i < lines.size && !lines[i].startsWith("@@")) {
                val body = lines[i]
                when {
                    body.startsWith("+++") || body.startsWith("---") -> {
                        // file header inside hunk scan shouldn't happen, skip
                    }
                    body.startsWith("+") -> {
                        result[lineNo] = if (hasRemoved) LineChangeType.MODIFIED else LineChangeType.ADDED
                        lineNo++
                    }
                    body.startsWith("-") -> {
                        hasRemoved = true
                    }
                    body.startsWith("\\") -> {
                        // "\ No newline at end of file" -- ignore
                    }
                    else -> {
                        lineNo++
                        hasRemoved = false
                    }
                }
                i++
            }
        }
        return result
    }
}

enum class LineChangeType { ADDED, MODIFIED, DELETED }
