---
mode: agent
description: Architecture decisions, module boundaries, layer rules, and Hilt scoping for Android Clean Architecture
---

# Android Architecture Guide

## Layer Dependency Law

```
┌─────────────────────────────┐
│       Presentation          │  ViewModels, Compose screens, Navigation
│  (knows Domain, not Data)   │
└──────────────┬──────────────┘
               │ depends on
               ▼
┌─────────────────────────────┐
│          Domain             │  Use cases, repository interfaces, domain models
│   (pure Kotlin — no deps)   │  ← arrows NEVER point outward from here
└──────────────▲──────────────┘
               │ implements
┌──────────────┴──────────────┐
│           Data              │  Repository impls, DTOs, mappers, Retrofit, Room
│  (knows Domain, not Pres.)  │
└─────────────────────────────┘
```

**Enforcement:** If `domain` imports anything from `data` or `presentation`, it is an architecture violation — refactor immediately.

---

## What Lives Where

| Artifact | Layer | Package | Hilt Scope |
|---|---|---|---|
| Domain model | domain | `domain/model/` | — |
| Repository interface | domain | `domain/repository/` | — |
| Use case | domain | `domain/usecase/` | — (constructor inject) |
| DTO | data | `data/remote/dto/` | — |
| Mapper | data | `data/remote/mapper/` | — |
| Room Entity | data | `data/local/entity/` | — |
| Room DAO | data | `data/local/dao/` | — |
| Repository impl | data | `data/repository/` | `@Singleton` |
| Retrofit API | data | `data/remote/api/` | `@Singleton` |
| ViewModel | presentation | `presentation/[feature]/` | `@HiltViewModel` |
| Compose screen | presentation | `presentation/[feature]/` | — |
| Navigation graph | presentation | `presentation/navigation/` | — |
| Hilt modules | di | `di/` | per-component |

---

## Hilt Scope Decision Guide

```
@Singleton            → App lifetime: repositories, Retrofit, Room, OkHttpClient
@ActivityRetainedScoped → ViewModel lifetime (set automatically by @HiltViewModel)
@ViewModelScoped      → Scoped to one ViewModel; use for ViewModel-specific helpers
@ActivityScoped       → Rare; only for Activity-tied state (e.g., PermissionManager)
@FragmentScoped       → Avoid with Compose; use ViewModelScoped instead
```

**Rule:** Default to `@Singleton` for data layer. Default to `@HiltViewModel` for ViewModels. Never manually scope a ViewModel.

---

## Hilt Module Patterns

```kotlin
// @Binds — for interface → implementation (preferred, compile-time safe)
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}

// @Provides — for third-party types or types requiring initialization
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)  // never hardcode URLs
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
```

**Note:** `BuildConfig.BASE_URL` must be set in `build.gradle.kts` via `buildConfigField`, sourced from `local.properties` — never a hardcoded string.

---

## Multi-Module Strategy

### When to extract a feature module
Extract to `:feature:[name]` when **any** of these are true:
- Feature has ≥ 3 use cases
- Feature can be independently navigated to via deep link
- Feature has its own navigation graph
- Build time benefit from parallel compilation is significant

### Recommended Module Structure
```
:app                    — application class, single-activity, top-level NavHost
:feature:home           — home screen feature
:feature:profile        — profile feature
:feature:settings       — settings feature
:core:domain            — all domain models, interfaces, use cases (pure Kotlin)
:core:data              — all repository implementations, DTOs, network, database
:core:ui                — shared Compose components, theme, typography
:core:common            — extensions, Result, BaseViewModel, dispatchers
```

### Module dependency rules
```
:feature:*     → :core:domain, :core:ui, :core:common
:core:data     → :core:domain, :core:common
:core:domain   → :core:common (or nothing)
:core:ui       → :core:common
:app           → all :feature:*, :core:data (for Hilt wiring)
```

---

## Navigation Architecture

- **Single Activity** pattern — `MainActivity` hosts the root `NavHost`
- Each feature defines its destinations in a `sealed class` or `object`:

```kotlin
// feature/home/navigation/HomeDestination.kt
sealed class HomeDestination(val route: String) {
    object List : HomeDestination("home/list")
    object Detail : HomeDestination("home/detail/{id}") {
        fun createRoute(id: String) = "home/detail/$id"
    }
}
```

- Navigation events are emitted as `UiEffect` from ViewModel — never call `NavController` from ViewModel directly
- `NavController` is only referenced in the screen-level composable or the NavHost

```kotlin
// Screen collects navigation effect and calls navController
LaunchedEffect(Unit) {
    viewModel.effect.collect { effect ->
        when (effect) {
            is HomeUiEffect.NavigateToDetail -> navController.navigate(
                HomeDestination.Detail.createRoute(effect.id)
            )
        }
    }
}
```

---

## Common Architecture Violations & Fixes

| Violation | Fix |
|---|---|
| ViewModel imports `android.content.Context` | Inject `@ApplicationContext` in data layer only; pass primitives to ViewModel |
| Use case imports `retrofit2.*` or `Room` | Violates domain purity — move logic to repository impl in data |
| Composable calls `viewModel.state.value` directly | Use `collectAsStateWithLifecycle()` at the screen composable level |
| Repository returns `Response<T>` (Retrofit) | Map to `Result<DomainModel>` before leaving data layer |
| `@Inject` constructor in use case has `android.*` import | Remove — use cases must be pure Kotlin |
| Hilt module co-located with its implementation class | Move to `di/` package |
| `NavController` stored in ViewModel | Never — pass navigation events as `UiEffect` |
| `LiveData` used instead of `StateFlow` | Migrate to `StateFlow` + `collectAsStateWithLifecycle()` |
| `GlobalScope.launch` in repository | Use `flowOn(dispatcher)` instead |
| Singleton ViewModel across features | Each screen/feature gets its own scoped ViewModel |

---

## Room + Retrofit Coexistence Pattern

```kotlin
// Single source of truth: Room is the source, Retrofit populates it
class ProductRepositoryImpl @Inject constructor(
    private val api: ProductApi,
    private val dao: ProductDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ProductRepository {

    override fun getProducts(): Flow<List<Product>> =
        dao.observeProducts()
            .map { entities -> entities.map { it.toDomain() } }
            .onStart { refreshFromNetwork() }
            .flowOn(dispatcher)

    private suspend fun refreshFromNetwork() {
        runCatching { api.getProducts() }
            .onSuccess { dtos -> dao.insertAll(dtos.map { it.toEntity() }) }
            .onFailure { /* emit cached data silently */ }
    }
}
```
