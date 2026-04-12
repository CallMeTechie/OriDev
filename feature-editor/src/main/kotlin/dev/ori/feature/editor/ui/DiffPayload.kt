package dev.ori.feature.editor.ui

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory payload for a diff viewer request. Lives in [DiffDataHolder].
 */
data class DiffPayload(
    val oldContent: String,
    val newContent: String,
    val oldTitle: String,
    val newTitle: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * In-memory store for diff content passed between screens via navigation.
 *
 * Data is NOT removed on [get] -- removal happens via [remove] when the consuming
 * ViewModel is cleared. This allows survival across configuration changes
 * (rotation) while still cleaning up.
 *
 * TTL of 10 minutes prevents leaks if a screen is never cleaned up (e.g. process
 * death followed by process restart with a stale entry).
 */
object DiffDataHolder {
    private val store = ConcurrentHashMap<String, DiffPayload>()
    private const val TTL_MS = 10 * 60 * 1000L

    fun put(id: String, payload: DiffPayload) {
        pruneExpired()
        store[id] = payload
    }

    fun get(id: String): DiffPayload? {
        pruneExpired()
        return store[id]
    }

    fun remove(id: String) {
        store.remove(id)
    }

    fun clear() {
        store.clear()
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        store.entries.removeAll { (_, payload) -> now - payload.createdAt > TTL_MS }
    }
}
