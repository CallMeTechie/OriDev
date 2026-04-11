package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.Connection
import dev.ori.domain.model.ConnectionStatus
import dev.ori.domain.repository.ConnectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConnectUseCaseTest {

    private lateinit var repository: ConnectionRepository
    private lateinit var connectUseCase: ConnectUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        connectUseCase = ConnectUseCase(repository)
    }

    @Test
    fun `connect success returns Connection`() = runTest {
        val expected = Connection(
            profileId = 1L,
            serverName = "Test Server",
            host = "example.com",
            status = ConnectionStatus.CONNECTED,
            connectedSince = 1000L
        )
        coEvery { repository.connect(1L) } returns expected

        val result = connectUseCase(1L)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(expected)
    }

    @Test
    fun `connect auth failure returns AuthenticationError`() = runTest {
        coEvery { repository.connect(1L) } throws
            RuntimeException("Authentication failed")

        val result = connectUseCase(1L)

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.AuthenticationError::class.java)
    }

    @Test
    fun `connect network failure returns NetworkError`() = runTest {
        coEvery { repository.connect(1L) } throws
            RuntimeException("Connection timed out")

        val result = connectUseCase(1L)

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.NetworkError::class.java)
    }

    @Test
    fun `connect with AppErrorException auth error preserves typed error`() = runTest {
        val authError = AppError.AuthenticationError("Bad credentials")
        coEvery { repository.connect(1L) } throws AppErrorException(authError)

        val result = connectUseCase(1L)

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.AuthenticationError::class.java)
    }

    @Test
    fun `connect with HostKeyUnknown rethrows exception`() = runTest {
        val hostKeyError = AppError.HostKeyUnknown("example.com", "fingerprint", "RSA")
        coEvery { repository.connect(1L) } throws AppErrorException(hostKeyError)

        var thrown: AppErrorException? = null
        try {
            connectUseCase(1L)
        } catch (e: AppErrorException) {
            thrown = e
        }

        assertThat(thrown).isNotNull()
        assertThat(thrown!!.error).isInstanceOf(AppError.HostKeyUnknown::class.java)
    }

    @Test
    fun `connect with HostKeyMismatch rethrows exception`() = runTest {
        val mismatchError = AppError.HostKeyMismatch("example.com", "expected", "actual")
        coEvery { repository.connect(1L) } throws AppErrorException(mismatchError)

        var thrown: AppErrorException? = null
        try {
            connectUseCase(1L)
        } catch (e: AppErrorException) {
            thrown = e
        }

        assertThat(thrown).isNotNull()
        assertThat(thrown!!.error).isInstanceOf(AppError.HostKeyMismatch::class.java)
    }
}
