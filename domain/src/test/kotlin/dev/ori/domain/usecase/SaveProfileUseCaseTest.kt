package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveProfileUseCaseTest {

    private lateinit var repository: ConnectionRepository
    private lateinit var saveProfileUseCase: SaveProfileUseCase

    private val validProfile = ServerProfile(
        id = 0L,
        name = "Test Server",
        host = "example.com",
        port = 22,
        protocol = Protocol.SSH,
        username = "user",
        authMethod = AuthMethod.PASSWORD,
        credentialRef = "cred-ref"
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        saveProfileUseCase = SaveProfileUseCase(repository)
    }

    @Test
    fun `valid new profile returns saved id`() = runTest {
        coEvery { repository.saveProfile(any()) } returns 42L

        val result = saveProfileUseCase(validProfile)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(42L)
        coVerify { repository.saveProfile(validProfile) }
    }

    @Test
    fun `blank name returns error`() = runTest {
        val profile = validProfile.copy(name = "  ")

        val result = saveProfileUseCase(profile)

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.StorageError::class.java)
        assertThat(error!!.message).contains("name")
    }

    @Test
    fun `invalid host returns error`() = runTest {
        val profile = validProfile.copy(host = "not a valid host!")

        val result = saveProfileUseCase(profile)

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.StorageError::class.java)
        assertThat(error!!.message).contains("host")
    }

    @Test
    fun `invalid port zero returns error`() = runTest {
        val profile = validProfile.copy(port = 0)

        val result = saveProfileUseCase(profile)

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.StorageError::class.java)
        assertThat(error!!.message).contains("port")
    }

    @Test
    fun `invalid port too high returns error`() = runTest {
        val profile = validProfile.copy(port = 70000)

        val result = saveProfileUseCase(profile)

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.StorageError::class.java)
        assertThat(error!!.message).contains("port")
    }

    @Test
    fun `existing profile calls update and returns id`() = runTest {
        val existingProfile = validProfile.copy(id = 5L)
        coEvery { repository.updateProfile(any()) } returns Unit

        val result = saveProfileUseCase(existingProfile)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(5L)
        coVerify { repository.updateProfile(existingProfile) }
    }
}
