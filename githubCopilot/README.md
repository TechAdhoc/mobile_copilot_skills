# Android MVVM Clean Architecture — GitHub Copilot Kit

Reusable GitHub Copilot instructions + copy-paste Kotlin base classes for Android projects.
Drop this into any Android project to enforce consistent Clean Architecture, MVI patterns, and security guardrails across the whole team.

---

## What's Included

```
.github/
├── copilot-instructions.md          # Always-loaded base context for Copilot
└── prompts/
    ├── android-development.prompt.md    # Skill: generate a complete feature
    ├── android-architect.prompt.md      # Skill: architecture decisions & layer rules
    ├── android-code-review.prompt.md    # Skill: review checklist (security → arch → compose)
    ├── android-code-test.prompt.md      # Skill: test strategy per layer
    └── android-unit-tests.prompt.md     # Skill: write ViewModel/UseCase/Mapper tests

core/
├── BaseViewModel.kt     # Abstract ViewModel with StateFlow + Channel MVI pattern
├── Result.kt            # Sealed Result<T> with helpers
└── Extensions.kt        # resultFlow{}, collectFlow{}

di/
└── DispatcherModule.kt  # @IoDispatcher, @MainDispatcher, @DefaultDispatcher qualifiers

test/
└── MainDispatcherRule.kt  # JUnit Rule for ViewModel coroutine testing

.gitignore               # Blocks secrets: keystores, local.properties, .env, google-services.json
```

---

## Setup — New Project

### 1. Copy files
```bash
cp -r .github/   <your-android-project>/.github/
cp    .gitignore <your-android-project>/.gitignore   # merge if one exists

# Copy Kotlin source files into your app module
cp core/*.kt  <your-project>/app/src/main/java/com/yourpackage/core/
cp di/*.kt    <your-project>/app/src/main/java/com/yourpackage/di/
cp test/*.kt  <your-project>/app/src/test/java/com/yourpackage/test/
```

### 2. Update package names
Find and replace `com.example.app` with your actual package name in all copied `.kt` files.

### 3. Add required Gradle dependencies

```kotlin
// app/build.gradle.kts
dependencies {
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-android-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Lifecycle (collectAsStateWithLifecycle)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Unit tests
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("com.google.truth:truth:1.4.2")
}
```

### 4. Verify Hilt is wired up
```kotlin
// Application class
@HiltAndroidApp
class App : Application()

// MainActivity
@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }
```

---

## Using the Copilot Skills

Open GitHub Copilot Chat and add a skill file with `#file:` before your prompt:

### Generate a new feature
```
#file:.github/prompts/android-development.prompt.md

Create a UserProfile feature. The user can view their name, avatar, and bio.
Tapping "Edit" navigates to an edit screen.
```

### Architecture decision
```
#file:.github/prompts/android-architect.prompt.md

Should the analytics tracker live in domain or data? It needs to fire events
when users complete onboarding steps.
```

### Code review
```
#file:.github/prompts/android-code-review.prompt.md

Review this file: #file:app/src/main/java/com/example/ProductViewModel.kt
```

### Test strategy (what to test)
```
#file:.github/prompts/android-code-test.prompt.md

What tests should I write for the Checkout feature? It calls a payment API,
updates a local Room cart, and navigates to a confirmation screen.
```

### Write unit tests
```
#file:.github/prompts/android-unit-tests.prompt.md

Write unit tests for CheckoutViewModel. The ViewModel uses PlaceOrderUseCase
and CartRepository. Actions: PlaceOrder, ClearCart. Effects: NavigateToConfirmation, ShowError.
```

---

## Architecture at a Glance

```
Presentation          Domain              Data
──────────────        ──────────          ──────────────────
ViewModel        →    UseCase        →    RepositoryImpl
Compose Screen        Repository ←        DTO + Mapper
Navigation            Domain Model        Retrofit / Room
UiState/Action/Effect Result<T>           DispatcherModule
```

**Laws:**
- `domain` never imports from `data` or `presentation`
- ViewModels always extend `BaseViewModel<S, A, E>`
- UI state classes always annotated `@Immutable`
- Always `collectAsStateWithLifecycle()`, never `collectAsState()`
- Navigation events are `UiEffect` — NavController never touches ViewModel
- No hardcoded API keys, tokens, or secrets anywhere in source

---

## BaseViewModel Pattern

```kotlin
// 1. Define contracts
@Immutable
data class LoginUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface LoginUiAction : UiAction {
    data class EmailChanged(val value: String) : LoginUiAction
    object Submit : LoginUiAction
}

sealed interface LoginUiEffect : UiEffect {
    object NavigateToHome : LoginUiEffect
    data class ShowError(val message: String) : LoginUiEffect
}

// 2. ViewModel
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : BaseViewModel<LoginUiState, LoginUiAction, LoginUiEffect>(LoginUiState()) {

    override fun onAction(action: LoginUiAction) = when (action) {
        is LoginUiAction.EmailChanged -> updateState { copy(email = action.value) }
        is LoginUiAction.Submit       -> submit()
    }

    private fun submit() {
        viewModelScope.launch {
            loginUseCase(state.value.email).collect { result ->
                when (result) {
                    is Result.Loading -> updateState { copy(isLoading = true) }
                    is Result.Success -> sendEffect(LoginUiEffect.NavigateToHome)
                    is Result.Error   -> {
                        updateState { copy(isLoading = false) }
                        sendEffect(LoginUiEffect.ShowError(result.exception.message.orEmpty()))
                    }
                }
            }
        }
    }
}

// 3. Screen (stateful) + Content (stateless)
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel(), onSuccess: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoginUiEffect.NavigateToHome -> onSuccess()
                is LoginUiEffect.ShowError      -> { /* show snackbar */ }
            }
        }
    }
    LoginContent(state = state, onAction = viewModel::onAction)
}
```

---

## Security Guardrails

| Rule | How enforced |
|---|---|
| No hardcoded secrets | `.gitignore` blocks `local.properties`, `*.keystore`, `.env`; code review pass 1 |
| No PII in logs | Code review pass 1 checks `Log.*` / `Timber.*` |
| HTTPS only | Code review flags `http://` URLs and `usesCleartextTraffic` |
| Sensitive prefs encrypted | Code review flags plain `SharedPreferences` for tokens |
| No direct push to main | Branch protection (configure in GitHub repo settings) |
| BuildConfig for URLs/keys | `android-architect.prompt.md` enforces `BuildConfig.BASE_URL` pattern |

---

## Copilot Instructions Design Notes

- **`copilot-instructions.md` is kept under ~130 lines** — it loads on every request; bloat here multiplies token cost across all completions
- **Skill files are loaded on demand** — only pay the token cost when you need that capability
- **`BaseViewModel` is in the base file, not a skill** — Copilot generates ViewModels constantly; having the signature always in context prevents it from hallucinating a different pattern
- **Security review is always first** — an architectural fix doesn't matter if credentials are leaking
