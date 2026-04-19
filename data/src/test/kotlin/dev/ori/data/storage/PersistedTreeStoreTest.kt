package dev.ori.data.storage

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Phase 15 Task 15.6 — JVM-only round-trip tests for [PersistedTreeStore].
 *
 * Uses the pure-JVM `PreferenceDataStoreFactory` (okio-backed) rather
 * than the Android extension, so this test runs under plain JUnit5 with
 * no Robolectric. The store writes to a temp directory that JUnit
 * cleans up after each test.
 *
 * @Suppress DataStoreFactory import remains here because the factory
 * type is referenced in the test fixture — Kotlin's import-compaction
 * would otherwise drop it.
 */
@Suppress("unused")
private typealias UnusedAnchor = DataStoreFactory

class PersistedTreeStoreTest {

    @TempDir
    lateinit var tempDir: File

    private fun newStore(): PersistedTreeStore {
        val file = File(tempDir, "trees_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { file },
        )
        return PersistedTreeStore(dataStore)
    }

    @Test
    fun `initial read is empty`() = runTest {
        val store = newStore()
        val uris = store.uris.first()
        assertThat(uris).isEmpty()
    }

    @Test
    fun `add persists uri and read returns it`() = runTest {
        val store = newStore()
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"

        store.add(uri)

        assertThat(store.uris.first()).containsExactly(uri)
    }

    @Test
    fun `add is idempotent`() = runTest {
        val store = newStore()
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"

        store.add(uri)
        store.add(uri)
        store.add(uri)

        assertThat(store.uris.first()).containsExactly(uri)
    }

    @Test
    fun `add multiple uris preserves all`() = runTest {
        val store = newStore()
        val a = "content://…/tree/primary%3AA"
        val b = "content://…/tree/primary%3AB"
        val c = "content://…/tree/primary%3AC"

        store.add(a)
        store.add(b)
        store.add(c)

        assertThat(store.uris.first()).containsExactly(a, b, c)
    }

    @Test
    fun `remove drops the uri`() = runTest {
        val store = newStore()
        val a = "content://…/tree/primary%3AA"
        val b = "content://…/tree/primary%3AB"
        store.add(a)
        store.add(b)

        store.remove(a)

        assertThat(store.uris.first()).containsExactly(b)
    }

    @Test
    fun `remove on absent uri is no-op`() = runTest {
        val store = newStore()
        val a = "content://…/tree/primary%3AA"
        store.add(a)

        store.remove("content://…/tree/primary%3ANotThere")

        assertThat(store.uris.first()).containsExactly(a)
    }

    @Test
    fun `uri set is observable via flow after add and remove`() = runTest {
        val store = newStore()
        val a = "content://…/tree/primary%3AA"
        val b = "content://…/tree/primary%3AB"

        assertThat(store.uris.first()).isEmpty()
        store.add(a)
        assertThat(store.uris.first()).containsExactly(a)
        store.add(b)
        assertThat(store.uris.first()).containsExactly(a, b)
        store.remove(a)
        assertThat(store.uris.first()).containsExactly(b)
    }
}
