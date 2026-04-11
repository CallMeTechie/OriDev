package dev.ori.core.common.result

import dev.ori.core.common.error.AppError

typealias AppResult<T> = Result<T>

fun <T> AppResult<T>.getAppError(): AppError? =
    exceptionOrNull()?.let { it as? AppErrorException }?.error

fun <T> AppResult<T>.onAppError(block: (AppError) -> Unit): AppResult<T> {
    getAppError()?.let(block)
    return this
}

fun <T> appSuccess(value: T): AppResult<T> = Result.success(value)

fun <T> appFailure(error: AppError): AppResult<T> = Result.failure(AppErrorException(error))

class AppErrorException(val error: AppError) : Exception(error.message, error.cause)
