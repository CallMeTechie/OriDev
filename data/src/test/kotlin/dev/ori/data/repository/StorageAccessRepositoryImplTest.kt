package dev.ori.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.documentfile.provider.DocumentFile
import com.google.common.truth.Truth.assertThat
import dev.ori.data.storage.PersistedTreeStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Phase 15 Task 15.6 — verifies the repo wrapper calls
 * `takePersistableUriPermission` with both READ and WRITE flags and
 * mirrors the URI into the persistent set. `Uri.parse` and
 * `DocumentFile.fromTreeUri` are stubbed via MockK static mocks to
 * avoid pulling in Robolectric for a pure-JVM test suite.
 */
class StorageAccessRepositoryImplTest {

    @TempDir
    lateinit var tempDir: File

    private fun newStore(): PersistedTreeStore {
        val file = File(tempDir, "trees_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { file },
        )
        return PersistedTreeStore(dataStore)
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `grant calls takePersistableUriPermission with READ and WRITE flags`() = runTest {
        mockkStatic(Uri::class, DocumentFile::class)
        val fakeUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns fakeUri
        every { DocumentFile.fromTreeUri(any(), any()) } returns null

        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.takePersistableUriPermission(any(), any()) } just Runs

        val store = newStore()
        val repo = StorageAccessRepositoryImpl(context, store)
        val raw = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"

        repo.grant(raw)

        val expected = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        verify { contentResolver.takePersistableUriPermission(fakeUri, expected) }
        assertThat(store.uris.first()).containsExactly(raw)
    }

    @Test
    fun `grant does not persist when takePersistableUriPermission throws`() = runTest {
        mockkStatic(Uri::class, DocumentFile::class)
        val fakeUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns fakeUri
        every { DocumentFile.fromTreeUri(any(), any()) } returns null

        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.takePersistableUriPermission(any(), any()) } throws
            SecurityException("not a persistable URI")

        val store = newStore()
        val repo = StorageAccessRepositoryImpl(context, store)

        repo.grant("content://bogus")

        assertThat(store.uris.first()).isEmpty()
    }

    @Test
    fun `revoke releases permission and removes from store`() = runTest {
        mockkStatic(Uri::class, DocumentFile::class)
        val fakeUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns fakeUri
        every { DocumentFile.fromTreeUri(any(), any()) } returns null

        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.takePersistableUriPermission(any(), any()) } just Runs
        every { contentResolver.releasePersistableUriPermission(any(), any()) } just Runs

        val store = newStore()
        val repo = StorageAccessRepositoryImpl(context, store)
        val raw = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
        repo.grant(raw)
        assertThat(store.uris.first()).containsExactly(raw)

        repo.revoke(raw)

        val expected = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        verify { contentResolver.releasePersistableUriPermission(fakeUri, expected) }
        assertThat(store.uris.first()).isEmpty()
    }

    @Test
    fun `revoke drops uri even if release throws`() = runTest {
        mockkStatic(Uri::class, DocumentFile::class)
        val fakeUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns fakeUri
        every { DocumentFile.fromTreeUri(any(), any()) } returns null

        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.takePersistableUriPermission(any(), any()) } just Runs
        every { contentResolver.releasePersistableUriPermission(any(), any()) } throws
            SecurityException("already released")

        val store = newStore()
        val repo = StorageAccessRepositoryImpl(context, store)
        val raw = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
        repo.grant(raw)

        repo.revoke(raw)

        assertThat(store.uris.first()).isEmpty()
    }
}
