---
mode: agent
description: Generate a complete Android feature following Clean Architecture — domain → data → presentation
---

# Android Feature Development

Generate a full feature following the 10-step scaffold below. Always complete every step in order. Replace `[Feature]` with the actual feature name throughout.

## Scaffold Checklist

1. **Domain model** — plain Kotlin `data class`, no Android imports
2. **Repository interface** — in `domain/`, returns `Flow<T>` or `suspend fun`
3. **Use case(s)** — in `domain/`, one class per use case, `operator fun invoke()`
4. **DTO** — in `data/`, annotated for serialization (`@SerializedName` / `@Json`)
5. **Mapper** — standalone `extension function` (DTO → domain), never embedded in DTO
6. **Repository implementation** — in `data/`, implements domain interface, uses `flowOn(dispatcher)`
7. **Hilt module** — in `di/`, `@Binds` for repo, `@Provides` for API/DAO
8. **UiState + UiAction + UiEffect** — sealed interfaces in `presentation/[feature]/`
9. **ViewModel** — extends `BaseViewModel`, `@HiltViewModel`
10. **Screen composable** — stateful screen + stateless content split

---

## Step-by-Step Templates

### 1 · Domain Model
```kotlin
// domain/model/[Feature].kt
data class [Feature](
    val id: String,
    val name: String
    // add fields as needed — no Android/Retrofit/Room types
)
```

### 2 · Repository Interface
```kotlin
// domain/repository/[Feature]Repository.kt
interface [Feature]Repository {
    fun get[Feature]s(): Flow<List<[Feature]>>
    suspend fun get[Feature]ById(id: String): [Feature]
}
```

### 3 · Use Case
```kotlin
// domain/usecase/Get[Feature]sUseCase.kt
class Get[Feature]sUseCase @Inject constructor(
    private val repository: [Feature]Repository
) {
    operator fun invoke(): Flow<Result<List<[Feature]>>> =
        repository.get[Feature]s()
            .map { Result.Success(it) as Result<List<[Feature]>> }
            .onStart { emit(Result.Loading) }
            .catch { emit(Result.Error(it)) }
}
```

### 4 · DTO
```kotlin
// data/remote/dto/[Feature]Dto.kt
data class [Feature]Dto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)
```

### 5 · Mapper
```kotlin
// data/remote/mapper/[Feature]Mapper.kt
fun [Feature]Dto.toDomain(): [Feature] = [Feature](
    id = id,
    name = name
)

fun List<[Feature]Dto>.toDomain(): List<[Feature]> = map { it.toDomain() }
```

### 6 · Repository Implementation
```kotlin
// data/repository/[Feature]RepositoryImpl.kt
class [Feature]RepositoryImpl @Inject constructor(
    private val api: [Feature]Api,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : [Feature]Repository {

    override fun get[Feature]s(): Flow<List<[Feature]>> = flow {
        emit(api.get[Feature]s().map { it.toDomain() })
    }.flowOn(dispatcher)

    override suspend fun get[Feature]ById(id: String): [Feature] =
        withContext(dispatcher) { api.get[Feature]ById(id).toDomain() }
}
```

### 7 · Hilt Module
```kotlin
// di/[Feature]Module.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class [Feature]Module {

    @Binds
    @Singleton
    abstract fun bind[Feature]Repository(
        impl: [Feature]RepositoryImpl
    ): [Feature]Repository

    companion object {
        @Provides
        @Singleton
        fun provide[Feature]Api(retrofit: Retrofit): [Feature]Api =
            retrofit.create([Feature]Api::class.java)
    }
}
```

### 8 · UiState · UiAction · UiEffect
```kotlin
// presentation/[feature]/[Feature]Contract.kt

@Immutable
data class [Feature]UiState(
    val items: List<[Feature]> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface [Feature]UiAction : UiAction {
    object Load : [Feature]UiAction
    data class Select(val id: String) : [Feature]UiAction
    object Retry : [Feature]UiAction
}

sealed interface [Feature]UiEffect : UiEffect {
    data class NavigateToDetail(val id: String) : [Feature]UiEffect
    data class ShowError(val message: String) : [Feature]UiEffect
}
```

### 9 · ViewModel
```kotlin
// presentation/[feature]/[Feature]ViewModel.kt
// imports: kotlinx.coroutines.Job  (for loadJob)
@HiltViewModel
class [Feature]ViewModel @Inject constructor(
    private val get[Feature]s: Get[Feature]sUseCase
) : BaseViewModel<[Feature]UiState, [Feature]UiAction, [Feature]UiEffect>(
    initialState = [Feature]UiState()
) {
    // Track the active load job so Retry cancels any in-flight collection first
    private var loadJob: Job? = null

    init { onAction([Feature]UiAction.Load) }

    override fun onAction(action: [Feature]UiAction) {
        when (action) {
            is [Feature]UiAction.Load,
            is [Feature]UiAction.Retry -> load()
            is [Feature]UiAction.Select -> sendEffect(
                [Feature]UiEffect.NavigateToDetail(action.id)
            )
        }
    }

    private fun load() {
        loadJob?.cancel()  // cancel any previous in-flight collection before starting a new one
        loadJob = viewModelScope.launch {
            get[Feature]s().collect { result ->
                when (result) {
                    is Result.Loading -> updateState { copy(isLoading = true, error = null) }
                    is Result.Success -> updateState {
                        copy(items = result.data, isLoading = false)
                    }
                    is Result.Error -> {
                        updateState { copy(isLoading = false) }
                        sendEffect([Feature]UiEffect.ShowError(
                            result.exception.message ?: "Unknown error"
                        ))
                    }
                }
            }
        }
    }
}
```

### 10 · Screen Composable
```kotlin
// presentation/[feature]/[Feature]Screen.kt

/**
 * Stateful screen — owns hiltViewModel() and effect collection.
 * Never pass ViewModel into child composables.
 */
@Composable
fun [Feature]Screen(
    viewModel: [Feature]ViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is [Feature]UiEffect.NavigateToDetail -> onNavigateToDetail(effect.id)
                is [Feature]UiEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    [Feature]Content(
        state = state,
        onAction = viewModel::onAction
    )
}

/**
 * Stateless content — purely renders state and forwards actions.
 * No ViewModel, no LaunchedEffect, no side effects.
 */
@Composable
private fun [Feature]Content(
    state: [Feature]UiState,
    onAction: ([Feature]UiAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> ErrorView(
                message = state.error,
                onRetry = { onAction([Feature]UiAction.Retry) }
            )
            else -> [Feature]List(
                items = state.items,
                onItemClick = { onAction([Feature]UiAction.Select(it.id)) }
            )
        }
    }
}
```

---

## Dispatcher Qualifier
Always inject dispatchers — never hardcode `Dispatchers.IO`:
```kotlin
// di/DispatcherModule.kt
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

@Module @InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @MainDispatcher fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    @Provides @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

## Navigation Registration
```kotlin
// presentation/navigation/AppNavGraph.kt
composable(route = [Feature]Destination.route) {
    [Feature]Screen(onNavigateToDetail = { id ->
        navController.navigate([Feature]DetailDestination.createRoute(id))
    })
}
```
