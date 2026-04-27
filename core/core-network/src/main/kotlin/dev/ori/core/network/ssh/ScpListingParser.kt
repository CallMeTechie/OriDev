package dev.ori.core.network.ssh

import dev.ori.core.network.model.RemoteFile
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object ScpListingParser {
    private val ISO_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private const val FIELDS_BEFORE_NAME = 6
    private const val PERMS_LEN = 10

    private data class ParsedFields(val perms: String, val uid: Int, val size: Long, val ts: Long)

    fun parse(output: String, parentPath: String, nameCache: NameCache): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("total ")) continue
            val tokens = splitNFields(line, FIELDS_BEFORE_NAME) ?: throw badFormat(line)
            val fields = parseFields(tokens) ?: throw badFormat(line)
            val rawName = tokens[6]
            val name = if (fields.perms.startsWith("l")) stripSymlinkTarget(rawName) else rawName
            if (name == "." || name == "..") continue
            out += RemoteFile(
                name = name,
                path = if (parentPath.endsWith("/")) "$parentPath$name" else "$parentPath/$name",
                isDirectory = fields.perms.startsWith("d"),
                size = fields.size,
                lastModified = fields.ts,
                permissions = fields.perms,
                owner = nameCache.resolveUid(fields.uid),
            )
        }
        return out
    }

    private fun parseFields(tokens: List<String>): ParsedFields? {
        val perms = tokens[0]
        if (perms.length != PERMS_LEN) return null
        val uid = tokens[2].toIntOrNull() ?: return null
        val size = tokens[4].toLongOrNull() ?: return null
        val ts = parseIso(tokens[5]) ?: return null
        val hasGid = tokens[3].toIntOrNull() != null
        return if (hasGid) ParsedFields(perms, uid, size, ts) else null
    }

    private fun splitNFields(line: String, n: Int): List<String>? {
        val out = mutableListOf<String>()
        var i = 0
        repeat(n) {
            while (i < line.length && line[i].isWhitespace()) i++
            if (i >= line.length) return null
            val start = i
            while (i < line.length && !line[i].isWhitespace()) i++
            out += line.substring(start, i)
        }
        while (i < line.length && line[i].isWhitespace()) i++
        if (i >= line.length) return null
        out += line.substring(i)
        return out
    }

    private fun parseIso(s: String): Long? = try {
        LocalDateTime.parse(s, ISO_LOCAL).toEpochSecond(ZoneOffset.UTC) * 1000L
    } catch (_: Exception) { null }

    private fun stripSymlinkTarget(s: String): String {
        val arrow = s.indexOf(" -> ")
        return if (arrow >= 0) s.substring(0, arrow) else s
    }

    private fun badFormat(line: String) = IOException(
        "Unsupported server: SCP listing requires GNU coreutils. " +
            "Set the connection protocol to SFTP for this server. Offending line: $line",
    )
}
