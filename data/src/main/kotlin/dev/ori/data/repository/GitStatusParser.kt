package dev.ori.data.repository

import dev.ori.domain.model.GitStatus
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Utility to detect git status for files in a local directory.
 *
 * Runs `git status --porcelain` via ProcessBuilder and parses the output.
 * Only works for local files -- remote (SFTP) files will not have git status.
 * If git is not installed or the directory is not a git repo, returns an empty map.
 */
object GitStatusParser {

    private const val GIT_TIMEOUT_SECONDS = 5L

    /**
     * Finds the git repository root for the given directory, if any.
     * Walks up the directory tree looking for a `.git` directory.
     */
    fun findGitRoot(directory: File): File? {
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
     * @return A map of absolute file path to [GitStatus], or empty map if not a git repo
     *         or git is unavailable.
     */
    fun parseStatus(directory: File): Map<String, GitStatus> {
        val gitRoot = findGitRoot(directory) ?: return emptyMap()

        return try {
            val process = ProcessBuilder("git", "status", "--porcelain")
                .directory(gitRoot)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return emptyMap()
            }

            if (process.exitValue() != 0) return emptyMap()

            val output = process.inputStream.bufferedReader().readText()
            parsePortelainOutput(output, gitRoot)
        } catch (_: Exception) {
            // git not installed, permission denied, etc.
            emptyMap()
        }
    }

    /**
     * Parses the porcelain output format.
     *
     * Format: `XY filename` where:
     * - X = index status, Y = working tree status
     * - `M ` or `MM` = modified (staged or unstaged)
     * - `A ` = staged (newly added)
     * - `??` = untracked
     */
    internal fun parsePortelainOutput(output: String, gitRoot: File): Map<String, GitStatus> {
        val result = mutableMapOf<String, GitStatus>()

        for (line in output.lines()) {
            if (line.length < 4) continue // minimum: "XY f"

            val indexStatus = line[0]
            val workTreeStatus = line[1]
            val relativePath = line.substring(3).trim()

            if (relativePath.isEmpty()) continue

            // Handle renamed files: "R  old -> new"
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
}
