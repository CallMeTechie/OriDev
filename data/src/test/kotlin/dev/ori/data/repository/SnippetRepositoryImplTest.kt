package dev.ori.data.repository

import com.google.common.truth.Truth.assertThat
import dev.ori.data.dao.CommandSnippetDao
import dev.ori.data.entity.CommandSnippetEntity
import dev.ori.domain.model.CommandSnippet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SnippetRepositoryImplTest {

    private val dao = mockk<CommandSnippetDao>(relaxed = true)
    private val repository = SnippetRepositoryImpl(dao)

    @Test
    fun `getSnippetsForServer maps all fields including watch and sortOrder`() = runTest {
        val entity = CommandSnippetEntity(
            id = 5L,
            serverProfileId = 10L,
            name = "Restart",
            command = "systemctl restart nginx",
            category = "ops",
            isWatchQuickCommand = true,
            sortOrder = 3,
        )
        every { dao.getForServer(10L) } returns flowOf(listOf(entity))

        val result = repository.getSnippetsForServer(10L).first()

        assertThat(result).hasSize(1)
        val snippet = result[0]
        assertThat(snippet.id).isEqualTo(5L)
        assertThat(snippet.serverProfileId).isEqualTo(10L)
        assertThat(snippet.name).isEqualTo("Restart")
        assertThat(snippet.command).isEqualTo("systemctl restart nginx")
        assertThat(snippet.category).isEqualTo("ops")
        assertThat(snippet.isWatchQuickCommand).isTrue()
        assertThat(snippet.sortOrder).isEqualTo(3)
    }

    @Test
    fun `addSnippet calls dao insert`() = runTest {
        val snippet = CommandSnippet(
            id = 0L,
            serverProfileId = 1L,
            name = "Deploy",
            command = "./deploy.sh",
            category = "deploy",
            isWatchQuickCommand = false,
            sortOrder = 0,
        )
        coEvery { dao.insert(any()) } returns 42L

        val result = repository.addSnippet(snippet)

        assertThat(result).isEqualTo(42L)
        coVerify {
            dao.insert(
                CommandSnippetEntity(
                    id = 0L,
                    serverProfileId = 1L,
                    name = "Deploy",
                    command = "./deploy.sh",
                    category = "deploy",
                    isWatchQuickCommand = false,
                    sortOrder = 0,
                )
            )
        }
    }
}
