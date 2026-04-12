package dev.ori.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.data.dao.SessionLogDao
import dev.ori.data.entity.SessionLogEntity
import dev.ori.domain.model.SessionRecording
import dev.ori.domain.repository.SessionRecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRecordingRepositoryImpl @Inject constructor(
    private val dao: SessionLogDao,
    @ApplicationContext private val context: Context,
) : SessionRecordingRepository {

    private data class ActiveWriter(
        val channel: Channel<ByteArray>,
        val writerJob: Job,
        val file: File,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeWriters = ConcurrentHashMap<Long, ActiveWriter>()

    override suspend fun startRecording(serverProfileId: Long): SessionRecording {
        val sessionsDir = File(context.filesDir, "sessions").apply { mkdirs() }
        val file = File(sessionsDir, "${UUID.randomUUID()}.log")
        file.createNewFile()

        val entity = SessionLogEntity(
            serverProfileId = serverProfileId,
            startedAt = System.currentTimeMillis(),
            logFilePath = file.absolutePath,
        )
        val id = dao.insert(entity)

        val channel = Channel<ByteArray>(capacity = CHANNEL_CAPACITY, onBufferOverflow = BufferOverflow.SUSPEND)
        val writerJob = scope.launch {
            file.outputStream().buffered().use { out ->
                for (bytes in channel) {
                    out.write(bytes)
                    out.flush()
                }
            }
        }

        activeWriters[id] = ActiveWriter(channel, writerJob, file)
        return SessionRecording(
            id = id,
            serverProfileId = serverProfileId,
            startedAt = entity.startedAt,
            endedAt = null,
            logFilePath = file.absolutePath,
        )
    }

    override suspend fun appendOutput(recordingId: Long, data: ByteArray) {
        val writer = activeWriters[recordingId] ?: return
        writer.channel.send(data.copyOf())
    }

    override suspend fun stopRecording(recordingId: Long) {
        val active = activeWriters.remove(recordingId) ?: return
        active.channel.close()
        active.writerJob.join()

        dao.getById(recordingId)?.let { entity ->
            dao.update(entity.copy(endedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun exportAsMarkdown(recordingId: Long): String {
        val entity = dao.getById(recordingId) ?: return ""
        val file = File(entity.logFilePath)
        if (!file.exists()) return ""

        val rawLog = file.readText(Charsets.UTF_8)
        return buildString {
            appendLine("# Terminal Session Recording")
            appendLine()
            appendLine("- **Started:** ${Instant.ofEpochMilli(entity.startedAt)}")
            entity.endedAt?.let {
                appendLine("- **Ended:** ${Instant.ofEpochMilli(it)}")
            }
            appendLine("- **Server Profile ID:** ${entity.serverProfileId}")
            appendLine()
            appendLine("```")
            append(rawLog)
            if (!rawLog.endsWith("\n")) appendLine()
            appendLine("```")
        }
    }

    override fun getRecordingsForServer(serverProfileId: Long): Flow<List<SessionRecording>> =
        dao.getForServer(serverProfileId).map { entities ->
            entities.map { e ->
                SessionRecording(
                    id = e.id,
                    serverProfileId = e.serverProfileId,
                    startedAt = e.startedAt,
                    endedAt = e.endedAt,
                    logFilePath = e.logFilePath,
                )
            }
        }

    companion object {
        private const val CHANNEL_CAPACITY = 256
    }
}
