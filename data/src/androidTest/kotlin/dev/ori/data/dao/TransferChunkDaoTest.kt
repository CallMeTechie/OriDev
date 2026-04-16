package dev.ori.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.db.OriDevDatabase
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.data.entity.TransferChunkEntity
import dev.ori.data.entity.TransferRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class TransferChunkDaoTest {

    private lateinit var db: OriDevDatabase
    private lateinit var dao: TransferChunkDao
    private var transferId: Long = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OriDevDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.transferChunkDao()

        val profileId = db.serverProfileDao().insert(
            ServerProfileEntity(
                name = "test",
                host = "host",
                port = 22,
                protocol = Protocol.SSH,
                username = "u",
                authMethod = AuthMethod.PASSWORD,
                credentialRef = "",
                sshKeyType = null,
                startupCommand = null,
                projectDirectory = null,
                claudeCodeModel = null,
                claudeMdPath = null,
            ),
        )
        transferId = db.transferRecordDao().insert(
            TransferRecordEntity(
                serverProfileId = profileId,
                sourcePath = "/a",
                destinationPath = "/b",
                direction = TransferDirection.UPLOAD,
                status = TransferStatus.QUEUED,
                totalBytes = 0L,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertChunk_newRow_insertsAndReturnsId() = runTest {
        val chunk = TransferChunkEntity(
            transferId = transferId, chunkIndex = 0,
            offsetBytes = 0, lengthBytes = 64 * 1024 * 1024,
        )
        val id = dao.upsert(chunk)
        assertThat(id).isGreaterThan(0)
        val rows = dao.getByTransferId(transferId)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].chunkIndex).isEqualTo(0)
    }

    @Test
    fun getByTransferId_multipleChunks_orderedByIndex() = runTest {
        dao.upsert(TransferChunkEntity(transferId = transferId, chunkIndex = 2, offsetBytes = 128_000_000, lengthBytes = 64_000_000))
        dao.upsert(TransferChunkEntity(transferId = transferId, chunkIndex = 0, offsetBytes = 0, lengthBytes = 64_000_000))
        dao.upsert(TransferChunkEntity(transferId = transferId, chunkIndex = 1, offsetBytes = 64_000_000, lengthBytes = 64_000_000))
        val chunks = dao.getByTransferId(transferId)
        assertThat(chunks.map { it.chunkIndex }).isEqualTo(listOf(0, 1, 2))
    }

    @Test
    fun updateChunkStatus_updatesRow() = runTest {
        val id = dao.upsert(TransferChunkEntity(transferId = transferId, chunkIndex = 0, offsetBytes = 0, lengthBytes = 100))
        dao.updateStatus(id, "COMPLETED", null)
        val chunk = dao.getByTransferId(transferId).single()
        assertThat(chunk.status).isEqualTo("COMPLETED")
        assertThat(chunk.attempts).isEqualTo(1)
    }

    @Test
    fun deleteByTransferId_removesAllRows() = runTest {
        dao.upsert(TransferChunkEntity(transferId = transferId, chunkIndex = 0, offsetBytes = 0, lengthBytes = 100))
        dao.upsert(TransferChunkEntity(transferId = transferId, chunkIndex = 1, offsetBytes = 100, lengthBytes = 100))
        dao.deleteByTransferId(transferId)
        assertThat(dao.getByTransferId(transferId)).isEmpty()
    }
}
