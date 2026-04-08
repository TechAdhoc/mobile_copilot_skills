package com.example.app.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base ViewModel for MVI / unidirectional data flow.
 *
 * Usage:
 *   class MyViewModel @Inject constructor(...) :
 *       BaseViewModel<MyUiState, MyUiAction, MyUiEffect>(MyUiState()) {
 *
 *       override fun onAction(action: MyUiAction) { ... }
 *   }
 *
 * @param S  UiState  — immutable snapshot of what the UI should display (@Immutable data class)
 * @param A  UiAction — user intents sent into the ViewModel (sealed interface)
 * @param E  UiEffect — one-shot side effects: navigation, snackbars (sealed interface + Channel)
 */
abstract class BaseViewModel<S : UiState, A : UiAction, E : UiEffect>(
    initialState: S
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(initialState)

    /**
     * Observe in composables via:
     *   val state by viewModel.state.collectAsStateWithLifecycle()
     */
    val state: StateFlow<S> = _state.asStateFlow()

    // ── One-shot Effects ──────────────────────────────────────────────────────
    private val _effect = Channel<E>(Channel.BUFFERED)

    /**
     * Collect in the stateful screen composable via:
     *   LaunchedEffect(Unit) { viewModel.effect.collect { ... } }
     *
     * Channel.BUFFERED ensures effects are not dropped if the collector
     * (LaunchedEffect) hasn't started yet — prevents navigation race conditions.
     */
    val effect: Flow<E> = _effect.receiveAsFlow()

    // ── Helpers for subclasses ────────────────────────────────────────────────

    /**
     * Apply a state mutation atomically.
     * Always use this instead of assigning to _state directly.
     *
     * Example:
     *   updateState { copy(isLoading = true) }
     */
    protected fun updateState(reducer: S.() -> S) = _state.update { it.reducer() }

    /**
     * Emit a one-shot effect (navigation, snackbar, dialog trigger).
     * Safe to call from any coroutine context.
     */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effect.send(effect) }
    }

    /**
     * Entry point for all user interactions.
     * All UI events funnel through here — no direct ViewModel method calls from Compose.
     *
     * Example:
     *   Button(onClick = { onAction(MyUiAction.Submit) })
     *   — or —
     *   MyContent(onAction = viewModel::onAction)
     */
    abstract fun onAction(action: A)
}

// ── Marker Interfaces ─────────────────────────────────────────────────────────

/** Marker for all UiState data classes. Annotate with @Immutable. */
interface UiState

/** Marker for all UiAction sealed interfaces. */
interface UiAction

/** Marker for all UiEffect sealed interfaces. */
interface UiEffect
