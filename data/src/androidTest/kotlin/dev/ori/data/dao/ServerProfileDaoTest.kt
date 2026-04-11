package dev.ori.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.data.db.OriDevDatabase
import dev.ori.data.entity.ServerProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerProfileDaoTest {

    private lateinit var db: OriDevDatabase
    private lateinit var dao: ServerProfileDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OriDevDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.serverProfileDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun testProfile(name: String = "test", host: String = "192.168.1.1") =
        ServerProfileEntity(
            name = name,
            host = host,
            port = 22,
            protocol = Protocol.SSH,
            username = "user",
            authMethod = AuthMethod.PASSWORD,
            credentialRef = "alias_test",
            sshKeyType = null,
            startupCommand = null,
            projectDirectory = null,
            claudeCodeModel = null,
            claudeMdPath = null,
        )

    @Test
    fun insert_andGetAll_returnsProfile() = runTest {
        dao.insert(testProfile())
        val profiles = dao.getAll().first()
        assertThat(profiles).hasSize(1)
        assertThat(profiles[0].name).isEqualTo("test")
    }

    @Test
    fun getById_returnsCorrectProfile() = runTest {
        val id = dao.insert(testProfile())
        val profile = dao.getById(id)
        assertThat(profile).isNotNull()
        assertThat(profile!!.host).isEqualTo("192.168.1.1")
    }

    @Test
    fun getById_nonExistent_returnsNull() = runTest {
        val profile = dao.getById(999)
        assertThat(profile).isNull()
    }

    @Test
    fun getCount_returnsCorrectCount() = runTest {
        dao.insert(testProfile("a"))
        dao.insert(testProfile("b"))
        assertThat(dao.getCount()).isEqualTo(2)
    }

    @Test
    fun getFavorites_returnsOnlyFavorites() = runTest {
        dao.insert(testProfile("normal"))
        dao.insert(testProfile("fav").copy(isFavorite = true))
        val favorites = dao.getFavorites().first()
        assertThat(favorites).hasSize(1)
        assertThat(favorites[0].name).isEqualTo("fav")
    }

    @Test
    fun delete_removesProfile() = runTest {
        val id = dao.insert(testProfile())
        val profile = dao.getById(id)!!
        dao.delete(profile)
        assertThat(dao.getById(id)).isNull()
    }

    @Test
    fun updateLastConnected_updatesTimestamp() = runTest {
        val id = dao.insert(testProfile())
        val before = dao.getById(id)!!.lastConnected
        assertThat(before).isNull()

        dao.updateLastConnected(id, 1234567890L)
        val after = dao.getById(id)!!.lastConnected
        assertThat(after).isEqualTo(1234567890L)
    }
}
