package com.example.app.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collect a Flow safely tied to the STARTED lifecycle state.
 * Use this in Fragments if not using Compose.
 *
 * Example (Fragment):
 *   viewLifecycleOwner.collectFlow(viewModel.state) { state -> ... }
 */
fun <T> LifecycleOwner.collectFlow(
    flow: Flow<T>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
) {
    lifecycleScope.launch {
        repeatOnLifecycle(minActiveState) {
            flow.collect { action(it) }
        }
    }
}

/**
 * Emit Loading, then execute block, wrapping result/error in Result.
 * Convenience for use case implementations.
 *
 * Example:
 *   override fun invoke() = resultFlow { repository.getItems() }
 */
fun <T> resultFlow(block: suspend () -> T): Flow<Result<T>> =
    kotlinx.coroutines.flow.flow {
        emit(Result.Loading)
        try {
            emit(Result.Success(block()))
        } catch (e: CancellationException) {
            throw e  // never swallow — this is how coroutines cancel
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }
