# Android Project — GitHub Copilot Instructions

## Stack
Kotlin · Jetpack Compose · Hilt · Coroutines + Flow · MVVM + MVI · Clean Architecture

## Package Structure
```
com.example.app/
  core/           # BaseViewModel, Result, extensions, utils
  data/           # repositories impl, data sources, DTOs, mappers, Room, Retrofit
  domain/         # use cases, repository interfaces, domain models
  presentation/   # ViewModels, Compose screens, navigation
  di/             # Hilt modules only
```
**Law:** `domain` must never import from `data` or `presentation`. Arrows flow inward only.

---

## Non-Negotiable Rules

### Architecture
- All ViewModels extend `BaseViewModel<S, A, E>` — never `ViewModel()` directly
- Repository interfaces live in `domain`; implementations in `data`
- Use cases are plain Kotlin classes with `@Inject constructor` — no Android framework imports
- Mappers are standalone `extension functions` or `object` — never embedded inside data classes
- No business logic in Composables or the data layer
- Use cases return `Flow<Result<T>>` or `suspend fun` returning `Result<T>`

### Jetpack Compose
- Always `collectAsStateWithLifecycle()` — never `collectAsState()`
- All UiState data classes annotated `@Immutable`
- Screen composable = stateful (holds `hiltViewModel()` + collects effects); content composable = stateless
- Effects collected via `LaunchedEffect(Unit)` **only for one-shot setup**; use stable keys for re-triggered effects
- Wrap expensive derived computations in `derivedStateOf { }`
- Never read `State` inside a lambda without `remember`
- Break composables when they exceed ~60 lines — single responsibility

### Hilt
- `@HiltViewModel` on every ViewModel
- `@Binds` for interface → implementation; `@Provides` for third-party / non-injectable types
- Hilt modules go in `di/` package only, never co-located with implementations

### Flow / Coroutines
- Repositories use `flowOn(dispatcher)` — never `viewModelScope.launch` inside a repository
- Hot flows shared with `stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)`
- No `runBlocking` anywhere except tests

### Security — always enforced
- **Never hardcode** API keys, tokens, secrets, or passwords — use `local.properties` + `BuildConfig`
- **Never log** (`Log.*` / `Timber.*`) tokens, user IDs, emails, or any PII
- **Never use HTTP** — enforce `android:usesCleartextTraffic="false"` and `network_security_config.xml`
- `SharedPreferences` with sensitive data → use `EncryptedSharedPreferences`
- `WebView` with user-supplied URLs → validate input; disable JS unless required
- Exported `Activity`/`BroadcastReceiver` → must declare required permissions in Manifest

---

## BaseViewModel Contract

```kotlin
// core/BaseViewModel.kt
abstract class BaseViewModel<S : UiState, A : UiAction, E : UiEffect>(
    initialState: S
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect: Flow<E> = _effect.receiveAsFlow()

    protected fun updateState(reducer: S.() -> S) = _state.update { it.reducer() }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effect.send(effect) }
    }

    abstract fun onAction(action: A)
}

interface UiState
interface UiAction
interface UiEffect
```

---

## Result Type

```kotlin
// core/Result.kt
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

---

## Recomposition Guard Rails

1. Never read `State` inside a lambda — read at composable scope, pass primitives down
2. Always `collectAsStateWithLifecycle()`, never `collectAsState()`
3. Unstable lambda captures → use `rememberUpdatedState` or stable function references
4. Large composables → extract stateless sub-composables
5. All UiState classes → `@Immutable`
6. Expensive derivations → `derivedStateOf { }`
7. `LaunchedEffect` key must be stable and semantically correct — `Unit` only for true one-shot setup

---

## Skill Files — Load on Demand

| Task | Add to chat |
|---|---|
| Generate a new feature | `#file:.github/prompts/android-development.prompt.md` |
| Architecture decisions | `#file:.github/prompts/android-architect.prompt.md` |
| Code review | `#file:.github/prompts/android-code-review.prompt.md` |
| Test strategy (what to test) | `#file:.github/prompts/android-code-test.prompt.md` |
| Write unit tests | `#file:.github/prompts/android-unit-tests.prompt.md` |
