---
mode: agent
description: Code review checklist — security, architecture, Compose recomposition, Flow/coroutine misuse, Kotlin anti-patterns
---

# Android Code Review

Review code in this exact pass order. Stop and report all issues in each pass before moving to the next. Use the output format below.

## Output Format
```
## [CRITICAL | WARNING | SUGGESTION] — <FileName.kt>:<line>
**Issue:** <what is wrong>
**Why:** <why it matters>
**Fix:** <corrected code snippet or action>
```

Severity guide:
- **CRITICAL** — security risk, data loss, crash, or architecture law violation
- **WARNING** — anti-pattern that will cause bugs, performance degradation, or maintenance pain
- **SUGGESTION** — style, naming, or minor improvement

---

## Pass 1 — Security (always first)

- [ ] **Hardcoded secret** — any string resembling a key, token, password, client_secret, `Bearer `, or base64 blob?
  → CRITICAL: Move to `local.properties` → `BuildConfig` field; never commit to VCS
- [ ] **PII/token logging** — `Log.*` or `Timber.*` printing userId, email, token, password, or any user-entered data?
  → CRITICAL: Remove immediately
- [ ] **HTTP traffic** — any `http://` URL, or `android:usesCleartextTraffic="true"` in Manifest?
  → CRITICAL: Enforce HTTPS; add `network_security_config.xml` with `cleartextTrafficPermitted="false"`
- [ ] **Unencrypted sensitive prefs** — `SharedPreferences` storing tokens, passwords, or PII?
  → CRITICAL: Replace with `EncryptedSharedPreferences`
- [ ] **WebView JS + user URL** — `setJavaScriptEnabled(true)` with `loadUrl(userInput)`?
  → CRITICAL: Validate/sanitize URL; disable JS unless strictly needed
- [ ] **Exported components** — `Activity`, `Service`, or `BroadcastReceiver` with `android:exported="true"` and no `permission`?
  → WARNING: Add `android:permission` or restrict export
- [ ] **Secrets in git-tracked files** — `google-services.json` with API keys, `*.keystore`, `.env`, `local.properties`?
  → CRITICAL: Add to `.gitignore`; rotate any exposed credentials

---

## Pass 2 — Architecture Violations

- [ ] **Domain imports Android** — any `android.*`, `retrofit2.*`, or `androidx.*` import in `domain/` package?
  → CRITICAL: Domain must be pure Kotlin — move to data or presentation layer
- [ ] **Use case returns Retrofit/Room type** — `Response<T>`, `Call<T>`, `Entity`, `Cursor`?
  → CRITICAL: Map to domain `Result<DomainModel>` before leaving data layer
- [ ] **Business logic in Composable** — conditionals, calculations, or IO calls inside a composable body?
  → CRITICAL: Move to ViewModel `onAction`
- [ ] **ViewModel references NavController or Context** — `android.content.Context` or `NavController` imported?
  → CRITICAL: Inject `@ApplicationContext` only in data layer; navigation via `UiEffect`
- [ ] **Repository impl in domain package** — `*RepositoryImpl` class found in `domain/`?
  → CRITICAL: Move to `data/repository/`
- [ ] **Hilt module outside di/ package** — `@Module` annotation not in the `di/` package?
  → WARNING: Move to `di/`
- [ ] **ViewModel not extending BaseViewModel** — `class *ViewModel : ViewModel()` directly?
  → WARNING: Must extend `BaseViewModel<S, A, E>`
- [ ] **GlobalScope used** — `GlobalScope.launch` anywhere outside a test?
  → CRITICAL: Use `viewModelScope` in ViewModel, `flowOn(dispatcher)` in repository

---

## Pass 3 — Compose Recomposition Issues

**BAD vs GOOD examples:**

```kotlin
// BAD — lambda captures unstable reference; whole parent recomposes on every state change
LazyColumn {
    items(state.items) { item ->
        ItemCard(
            item = item,
            onClick = { viewModel.onAction(Action.Select(item.id)) } // new lambda each recomposition
        )
    }
}

// GOOD — stable function reference; no unnecessary recomposition
LazyColumn {
    items(state.items, key = { it.id }) { item ->  // key prevents full list recomposition
        ItemCard(
            item = item,
            onClick = { onAction(Action.Select(item.id)) } // onAction is stable (::onAction ref)
        )
    }
}
```

- [ ] **`collectAsState()` used** — must be `collectAsStateWithLifecycle()`
  → WARNING: `collectAsState()` doesn't respect Android lifecycle; leaks collection in background
- [ ] **UiState missing `@Immutable`** — data class without `@Immutable` annotation?
  → WARNING: Compose cannot skip recomposition without stability guarantee
- [ ] **State read inside lambda** — reading `state.value` or `flow.collectAsState()` inside a lambda body?
  → WARNING: Extract to composable scope; pass primitive down
- [ ] **Unstable lambda in composable param** — inline lambda `{ viewModel.doX() }` passed to a child composable?
  → WARNING: Use `viewModel::onAction` reference or `rememberUpdatedState`
- [ ] **Missing `key` in `LazyColumn`/`LazyRow`** — `items(list)` without `key = { it.id }`?
  → WARNING: Causes full list recomposition on data changes
- [ ] **`derivedStateOf` missing** — expensive computation (sorting, filtering, mapping) inside composable body?
  → WARNING: Wrap in `val derived = remember { derivedStateOf { ... } }`
- [ ] **`LaunchedEffect(Unit)` for re-triggered effect** — using `Unit` key for something that should re-run on state change?
  → WARNING: Use the correct trigger value as key (e.g., `LaunchedEffect(userId)`)
- [ ] **Composable > 60 lines** — large monolithic composable doing layout, logic, and state together?
  → SUGGESTION: Extract stateless sub-composables, each with single responsibility

---

## Pass 4 — Flow / Coroutine Misuse

- [ ] **`viewModelScope.launch` in repository** — repository using ViewModel scope?
  → CRITICAL: Repositories must use `flowOn(dispatcher)` or `withContext(dispatcher)`
- [ ] **`runBlocking` outside tests** — `runBlocking` in production code?
  → CRITICAL: Replace with proper coroutine or `suspend fun`
- [ ] **`.value` on StateFlow in composable** — `viewModel.state.value` instead of `collectAsStateWithLifecycle()`?
  → WARNING: Won't trigger recomposition reliably
- [ ] **`Channel` used as state holder** — `MutableStateFlow` used where a `Channel` is needed for events (or vice versa)?
  → WARNING: State (persisted, replayed) → `StateFlow`; one-shot events → `Channel`
- [ ] **Shared hot flow without `stateIn`/`shareIn`** — upstream cold flow shared across multiple collectors?
  → WARNING: Use `stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)`
- [ ] **Exception swallowed in flow** — `.catch { }` with empty body?
  → WARNING: Emit `Result.Error` or at minimum log the throwable

---

## Pass 5 — Hilt Misuse

- [ ] **`@Provides` where `@Binds` should be used** — providing an interface by instantiating its impl manually?
  → SUGGESTION: Use `@Binds` — it's compile-time verified and avoids object allocation
- [ ] **`@Inject` on ViewModel constructor without `@HiltViewModel`** — ViewModel missing `@HiltViewModel`?
  → CRITICAL: Hilt cannot provide it correctly; add `@HiltViewModel`
- [ ] **Scoped dependency in wider scope** — `@ViewModelScoped` dependency injected into `@Singleton`?
  → CRITICAL: Scope hierarchy violation — widen the dependency scope or narrow the consumer

---

## Pass 6 — Kotlin Anti-Patterns

- [ ] **`!!` (non-null assertion)** — `someValue!!` without a prior null check?
  → WARNING: Replace with `?: throw IllegalStateException(...)`, `requireNotNull()`, or safe call
- [ ] **`apply`/`also`/`let` overused** — chained scope functions making code unreadable?
  → SUGGESTION: Flatten where clarity improves
- [ ] **`when` without `else` on non-sealed type** — `when (someEnum)` missing `else`?
  → WARNING: Add `else` branch or use a sealed type
- [ ] **`suspend` function named like a getter** — `suspend fun getUser()` that should be `suspend fun fetchUser()`?
  → SUGGESTION: Prefix suspending network calls with `fetch`/`load`/`sync`
- [ ] **Mutable collection exposed from ViewModel** — `_items: MutableList<T>` accessible from outside?
  → CRITICAL: Expose only immutable type; mutation via `updateState { }`
