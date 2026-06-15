package com.mediaproject.core.repo

sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Failure(val message: String) : AppResult<Nothing>()

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun getOrElse(default: @UnsafeVariance T): T = if (this is Success) value else default

    val isSuccess: Boolean get() = this is Success

    fun onSuccess(action: (T) -> Unit): AppResult<T> = also { if (this is Success) action(value) }
    fun onFailure(action: (String) -> Unit): AppResult<T> = also { if (this is Failure) action(message) }
}

fun <T> runCatchingResult(block: () -> T): AppResult<T> =
    try { AppResult.Success(block()) } catch (e: Exception) { AppResult.Failure(e.message ?: "오류 발생") }