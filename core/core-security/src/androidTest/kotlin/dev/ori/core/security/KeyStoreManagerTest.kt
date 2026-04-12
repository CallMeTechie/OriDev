package dev.ori.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyStoreManagerTest {

    private lateinit var context: Context
    private lateinit var manager: KeyStoreManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = KeyStoreManager(context)
    }

    @After
    fun tearDown() = runTest {
        manager.deleteCredential(ALIAS_PASSWORD)
        manager.deleteCredential(ALIAS_SSH)
        manager.deleteCredential(ALIAS_PERSIST)
    }

    @Test
    fun storeAndRetrievePassword_works() = runTest {
        val password = "s3cret-pass".toCharArray()
        manager.storePassword(ALIAS_PASSWORD, password)

        val retrieved = manager.getPassword(ALIAS_PASSWORD)
        assertThat(retrieved).isNotNull()
        assertThat(String(retrieved!!)).isEqualTo("s3cret-pass")
    }

    @Test
    fun storeSshKey_persistsBytes() = runTest {
        val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        manager.storeSshKey(ALIAS_SSH, key)

        val retrieved = manager.getSshKey(ALIAS_SSH)
        assertThat(retrieved).isEqualTo(key)
    }

    @Test
    fun deleteCredential_removesFromStore() = runTest {
        manager.storePassword(ALIAS_PASSWORD, "temp".toCharArray())
        assertThat(manager.hasCredential(ALIAS_PASSWORD)).isTrue()

        manager.deleteCredential(ALIAS_PASSWORD)
        assertThat(manager.hasCredential(ALIAS_PASSWORD)).isFalse()
        assertThat(manager.getPassword(ALIAS_PASSWORD)).isNull()
    }

    @Test
    fun hasCredential_returnsTrueAfterStore() = runTest {
        assertThat(manager.hasCredential(ALIAS_PASSWORD)).isFalse()
        manager.storePassword(ALIAS_PASSWORD, "abc".toCharArray())
        assertThat(manager.hasCredential(ALIAS_PASSWORD)).isTrue()
    }

    @Test
    fun newInstance_canReadOldCredentials() = runTest {
        manager.storePassword(ALIAS_PERSIST, "persisted".toCharArray())

        // Simulate app restart by creating a new manager against the same context.
        val newManager = KeyStoreManager(context)
        val retrieved = newManager.getPassword(ALIAS_PERSIST)

        assertThat(retrieved).isNotNull()
        assertThat(String(retrieved!!)).isEqualTo("persisted")
    }

    private companion object {
        const val ALIAS_PASSWORD = "test_password_alias"
        const val ALIAS_SSH = "test_ssh_alias"
        const val ALIAS_PERSIST = "test_persist_alias"
    }
}
