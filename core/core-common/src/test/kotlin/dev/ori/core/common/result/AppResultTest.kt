package dev.ori.core.common.result

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import org.junit.jupiter.api.Test

class AppResultTest {

    @Test
    fun appSuccess_isSuccess() {
        val result = appSuccess("hello")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("hello")
    }

    @Test
    fun appFailure_isFailure() {
        val result = appFailure<String>(AppError.NetworkError("timeout"))
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun getAppError_returnsError() {
        val error = AppError.AuthenticationError("bad password")
        val result = appFailure<String>(error)
        assertThat(result.getAppError()).isEqualTo(error)
    }

    @Test
    fun getAppError_onSuccess_returnsNull() {
        val result = appSuccess("ok")
        assertThat(result.getAppError()).isNull()
    }

    @Test
    fun onAppError_callsBlock() {
        var captured: AppError? = null
        val error = AppError.PermissionDenied("nope")
        appFailure<Unit>(error).onAppError { captured = it }
        assertThat(captured).isEqualTo(error)
    }

    @Test
    fun onAppError_onSuccess_doesNotCallBlock() {
        var called = false
        appSuccess("ok").onAppError { called = true }
        assertThat(called).isFalse()
    }
}
