---
mode: agent
description: Test strategy per architecture layer — what to test, test types, coverage targets, and Gradle dependencies
---

# Android Test Strategy

## Testing Pyramid

```
            /\
           /  \
          / E2E \        ~10% — Compose UI tests, Espresso
         /--------\
        /Integration\    ~20% — Repository + Room/Retrofit (real DB, mock HTTP)
       /--------------\
      /   Unit Tests   \  ~70% — ViewModels, UseCases, Mappers (JVM only)
     /------------------\
```

**Target:** Every use case and ViewModel must have unit tests before merging. Integration and E2E are additive.

---

## What to Test — By Layer

### Domain Layer — Use Cases
**Test type:** JUnit unit test (pure JVM, no Robolectric)
**What to cover:**
- Happy path: valid repository emission → `Result.Success` emitted
- Error path: repository throws → `Result.Error` emitted
- Loading state emitted before result
- Edge cases: empty list, null fields, boundary values

**What NOT to test:** The repository implementation — that is tested separately.

```
GetProductsUseCaseTest
  ✓ invoke emits Loading then Success when repository returns data
  ✓ invoke emits Error when repository throws IOException
  ✓ invoke emits empty Success when repository returns empty list
```

### Data Layer — Repositories & Mappers
**Test type:** Unit test with MockK (repository); plain assertion (mapper)

**Repository — what to cover:**
- Correct DTO → domain model mapping applied
- `flowOn` dispatcher is injectable (use `UnconfinedTestDispatcher`)
- Error from API propagated as exception in flow
- Room single-source-of-truth: DAO observed, API result inserted

**Mapper — what to cover:**
- Every DTO field maps to correct domain field
- Null/optional fields use correct defaults
- List mapper delegates to single-item mapper

```
ProductMapperTest
  ✓ toDomain maps id correctly
  ✓ toDomain maps name correctly
  ✓ toDomain with null price defaults to 0.0
  ✓ List<ProductDto>.toDomain maps all items

ProductRepositoryImplTest
  ✓ getProducts emits mapped domain models when API succeeds
  ✓ getProducts propagates exception when API throws
```

### Presentation Layer — ViewModels
**Test type:** JUnit unit test with `MainDispatcherRule`, MockK, Turbine

**What to cover:**
- Each `UiAction` produces the correct `UiState` mutations
- Each `UiAction` that triggers a `UiEffect` emits the correct effect
- `isLoading` transitions: false → true → false
- Error state set correctly when use case emits `Result.Error`
- Error effect emitted when use case emits `Result.Error`
- Initial state is correct

```
ProductListViewModelTest
  ✓ initial state has empty list, isLoading false, no error
  ✓ Load action sets isLoading true while fetching
  ✓ Load action success updates items and clears loading
  ✓ Load action error clears loading and sends ShowError effect
  ✓ Select action sends NavigateToDetail effect with correct id
  ✓ Retry action reloads data
```

### Compose UI Layer — Screen Tests
**Test type:** `@HiltAndroidTest` with `createAndroidComposeRule`

**What to cover (smoke + interaction only):**
- Screen renders without crash given initial state
- Loading indicator visible when `isLoading = true`
- Error view visible when `error != null`
- Empty state visible when `items = emptyList()`
- Tap on item triggers the correct action (verify via fake ViewModel)

**Skip:** Pixel-perfect layout tests, animation timing.

---

## Test Naming Convention

```
fun `[subject] [condition] [expected result]`()

// Examples:
fun `invoke when repository returns empty list then emits Success with empty data`()
fun `onAction Load when useCase emits error then state has no loading and effect ShowError sent`()
fun `toDomain when price is null then domain price is zero`()
fun `getProducts when api throws then flow emits exception`()
```

Pattern: **subject → condition → expected result** — always use backtick names for readability.

---

## Gradle Dependencies

```kotlin
// build.gradle.kts — module level

dependencies {
    // Unit tests (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("com.google.truth:truth:1.4.2")           // fluent assertions

    // Android unit tests (requires Robolectric — avoid unless needed)
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("org.robolectric:robolectric:4.12.1")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.51")

    // Debug
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## Hilt Test Setup

### Unit test (no Hilt needed — inject manually with MockK)
```kotlin
class ProductListViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val getProducts: GetProductsUseCase = mockk()
    private lateinit var viewModel: ProductListViewModel

    @Before fun setUp() {
        viewModel = ProductListViewModel(getProducts)
    }
}
```

### Instrumented test (Hilt)
```kotlin
@HiltAndroidTest
class ProductListScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() { hiltRule.inject() }

    @Test fun `screen shows loading indicator on launch`() {
        composeRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }
}
```

### Fake module for instrumented tests
```kotlin
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ProductModule::class])
abstract class FakeProductModule {
    @Binds @Singleton
    abstract fun bindFakeRepository(fake: FakeProductRepository): ProductRepository
}
```

---

## Test Coverage Targets

| Layer | Target | Priority |
|---|---|---|
| Use cases | 100% happy + error paths | P0 — block merge if missing |
| ViewModels | 100% of `onAction` branches | P0 — block merge if missing |
| Mappers | 100% field coverage | P1 |
| Repositories | Happy + error per public fun | P1 |
| Compose screens | Smoke test (renders, 1 interaction) | P2 |
| E2E flows | Critical user journeys only | P3 |

---

## Test File Location

```
src/
  test/java/com.example.app/
    domain/usecase/       → GetProductsUseCaseTest.kt
    data/mapper/          → ProductMapperTest.kt
    data/repository/      → ProductRepositoryImplTest.kt
    presentation/[feat]/  → [Feature]ViewModelTest.kt

  androidTest/java/com.example.app/
    presentation/[feat]/  → [Feature]ScreenTest.kt
    di/                   → Fake*Module.kt
```

Use `#file:.github/prompts/android-unit-tests.prompt.md` to generate the actual test code.
