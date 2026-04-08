package com.example.app.core

/**
 * Project-wide result wrapper for use cases and repositories.
 *
 * Usage in a use case:
 *   operator fun invoke(): Flow<Result<List<Product>>> =
 *       repository.getProducts()
 *           .map  { Result.Success(it) as Result<List<Product>> }
 *           .onStart { emit(Result.Loading) }
 *           .catch { emit(Result.Error(it)) }
 *
 * Usage in a ViewModel:
 *   useCase().collect { result ->
 *       when (result) {
 *           is Result.Loading  -> updateState { copy(isLoading = true) }
 *           is Result.Success  -> updateState { copy(items = result.data, isLoading = false) }
 *           is Result.Error    -> {
 *               updateState { copy(isLoading = false) }
 *               sendEffect(MyUiEffect.ShowError(result.exception.message.orEmpty()))
 *           }
 *       }
 *   }
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// ── Extension helpers ─────────────────────────────────────────────────────────

/** Returns the data or null without requiring a when expression. */
fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data

/** Returns the data or a default value. */
fun <T> Result<T>.getOrDefault(default: T): T = getOrNull() ?: default

/** True only when this is Result.Success. */
val <T> Result<T>.isSuccess: Boolean get() = this is Result.Success

/** True only when this is Result.Error. */
val <T> Result<T>.isError: Boolean get() = this is Result.Error

/** True only when this is Result.Loading. */
val <T> Result<T>.isLoading: Boolean get() = this is Result.Loading

/** Transform the data inside Success without unwrapping. */
inline fun <T, R> Result<T>.mapSuccess(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error   -> this
    is Result.Loading -> this
}
