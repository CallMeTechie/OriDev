package dev.ori.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.ori.data.db.OriDevDatabase
import dev.ori.data.entity.KnownHostEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnownHostDaoTest {

    private lateinit var db: OriDevDatabase
    private lateinit var dao: KnownHostDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OriDevDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.knownHostDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_andFind_returnsHost() = runTest {
        dao.upsert(testHost())
        val found = dao.find("example.com", 22)
        assertThat(found).isNotNull()
        assertThat(found!!.fingerprint).isEqualTo("SHA256:abc123def456")
    }

    @Test
    fun find_nonExistent_returnsNull() = runTest {
        assertThat(dao.find("unknown.com", 22)).isNull()
    }

    @Test
    fun upsert_sameHostPort_replacesEntry() = runTest {
        dao.upsert(testHost(fingerprint = "old"))
        dao.upsert(testHost().copy(fingerprint = "new"))
        val found = dao.find("example.com", 22)
        assertThat(found!!.fingerprint).isEqualTo("new")
    }

    @Test
    fun getAll_returnsAllHosts() = runTest {
        dao.upsert(testHost("a.com"))
        dao.upsert(testHost("b.com"))
        val all = dao.getAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun delete_removesHost() = runTest {
        dao.upsert(testHost())
        val host = dao.find("example.com", 22)!!
        dao.delete(host)
        assertThat(dao.find("example.com", 22)).isNull()
    }

    private fun testHost(host: String = "example.com", port: Int = 22, fingerprint: String = "SHA256:abc123def456") =
        KnownHostEntity(host = host, port = port, keyType = "ssh-ed25519", fingerprint = fingerprint)
}
