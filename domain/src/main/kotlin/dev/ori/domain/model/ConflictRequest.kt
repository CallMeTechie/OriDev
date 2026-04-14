package dev.ori.domain.model

/**
 * Phase 12 P12.2 — transient request surfaced by the transfer engine when a
 * destination path already exists and `overwriteMode == "ask"`. Suspended
 * until the user picks a [ConflictResolution] via `ResolveConflictUseCase`.
 *
 * Timestamps are epoch millis (Long) because the `domain` module is pure
 * Kotlin/JVM with no `kotlinx.datetime` dependency.
 */
data class ConflictRequest(
    val id: String,
    val transferId: Long,
    val conflictedPath: String,
    val existingSize: Long,
    val existingLastModified: Long,
)

enum class ConflictResolution { OVERWRITE, SKIP, RENAME, CANCEL }
