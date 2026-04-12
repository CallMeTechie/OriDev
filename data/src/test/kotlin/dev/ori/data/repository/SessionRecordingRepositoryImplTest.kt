package dev.ori.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import dev.ori.data.dao.SessionLogDao
import dev.ori.data.entity.SessionLogEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class SessionRecordingRepositoryImplTest {

    private fun newFakeDao(): SessionLogDao {
        val dao = mockk<SessionLogDao>(relaxed = true)
        val nextId = AtomicLong(1)
        val store = mutableMapOf<Long, SessionLogEntity>()
        coEvery { dao.insert(any()) } answers {
            val id = nextId.getAndIncrement()
            val e = firstArg<SessionLogEntity>().copy(id = id)
            store[id] = e
            id
        }
        coEvery { dao.getById(any()) } answers { store[firstArg()] }
        coEvery { dao.update(any()) } answers {
            val e = firstArg<SessionLogEntity>()
            store[e.id] = e
        }
        return dao
    }

    private fun contextFor(tempDir: File): Context {
        val ctx = mockk<Context>()
        every { ctx.filesDir } returns tempDir
        return ctx
    }

    @Test
    fun `startRecording creates log file and inserts entity`(@TempDir tempDir: File) = runTest {
        val dao = newFakeDao()
        val repo = SessionRecordingRepositoryImpl(dao, contextFor(tempDir))

        val recording = repo.startRecording(serverProfileId = 7L)

        assertThat(recording.serverProfileId).isEqualTo(7L)
        assertThat(recording.id).isEqualTo(1L)
        val file = File(recording.logFilePath)
        assertThat(file.exists()).isTrue()
        assertThat(file.parentFile?.name).isEqualTo("sessions")
        coVerify { dao.insert(any()) }

        repo.stopRecording(recording.id)
    }

    @Test
    fun `appendOutput writes bytes to file`(@TempDir tempDir: File) = runTest {
        val dao = newFakeDao()
        val repo = SessionRecordingRepositoryImpl(dao, contextFor(tempDir))

        val recording = repo.startRecording(1L)
        repo.appendOutput(recording.id, "hello".toByteArray())
        repo.stopRecording(recording.id)

        val content = File(recording.logFilePath).readText()
        assertThat(content).isEqualTo("hello")
    }

    @Test
    fun `stopRecording flushes buffered output and updates endedAt`(@TempDir tempDir: File) = runTest {
        val dao = newFakeDao()
        val repo = SessionRecordingRepositoryImpl(dao, contextFor(tempDir))

        val recording = repo.startRecording(1L)
        repo.appendOutput(recording.id, "part1".toByteArray())
        repo.appendOutput(recording.id, "part2".toByteArray())
        val updateSlot = slot<SessionLogEntity>()
        coEvery { dao.update(capture(updateSlot)) } returns Unit

        repo.stopRecording(recording.id)

        assertThat(File(recording.logFilePath).readText()).isEqualTo("part1part2")
        assertThat(updateSlot.captured.endedAt).isNotNull()
    }

    @Test
    fun `exportAsMarkdown wraps raw log in markdown`(@TempDir tempDir: File) = runTest {
        val dao = newFakeDao()
        val repo = SessionRecordingRepositoryImpl(dao, contextFor(tempDir))

        val recording = repo.startRecording(42L)
        repo.appendOutput(recording.id, "ls -la\n".toByteArray())
        repo.stopRecording(recording.id)

        val md = repo.exportAsMarkdown(recording.id)

        assertThat(md).contains("# Terminal Session Recording")
        assertThat(md).contains("Server Profile ID:** 42")
        assertThat(md).contains("```")
        assertThat(md).contains("ls -la")
    }

    @Test
    fun `appendOutput after stopRecording does not crash`(@TempDir tempDir: File) = runTest {
        val dao = newFakeDao()
        val repo = SessionRecordingRepositoryImpl(dao, contextFor(tempDir))

        val recording = repo.startRecording(1L)
        repo.stopRecording(recording.id)

        // Should be a no-op (active writer removed).
        repo.appendOutput(recording.id, "late".toByteArray())
    }

    @Test
    fun `multiple appends preserve order`(@TempDir tempDir: File) = runTest {
        val dao = newFakeDao()
        val repo = SessionRecordingRepositoryImpl(dao, contextFor(tempDir))

        val recording = repo.startRecording(1L)
        repeat(10) { i ->
            repo.appendOutput(recording.id, "chunk$i|".toByteArray())
        }
        repo.stopRecording(recording.id)

        val expected = (0 until 10).joinToString("") { "chunk$it|" }
        assertThat(File(recording.logFilePath).readText()).isEqualTo(expected)
    }

    @Test
    fun `concurrent appends preserve all bytes`(@TempDir tempDir: File) = runTest {
        val dao = newFakeDao()
        val repo = SessionRecordingRepositoryImpl(dao, contextFor(tempDir))

        val recording = repo.startRecording(1L)
        val jobs = (0 until 50).map { i ->
            async { repo.appendOutput(recording.id, "x".toByteArray()) }
        }
        jobs.awaitAll()
        repo.stopRecording(recording.id)

        val content = File(recording.logFilePath).readText()
        assertThat(content.length).isEqualTo(50)
        assertThat(content.all { it == 'x' }).isTrue()
    }
}
