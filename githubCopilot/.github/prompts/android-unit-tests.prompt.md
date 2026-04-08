---
mode: agent
description: Write unit tests for Android — ViewModel (Turbine + MockK), UseCase, Mapper, coroutines with MainDispatcherRule
---

# Android Unit Test Writer

Generate complete, runnable unit tests. Use the templates below as exact patterns. Replace `[Feature]` with the actual feature name.

---

## Required Infrastructure

### MainDispatcherRule (add once to `src/test/java/.../util/`)

Copy `test/MainDispatcherRule.kt` from this repo. Uses `UnconfinedTestDispatcher` (coroutines 1.7+) which runs coroutines eagerly — no `advanceUntilIdle()` needed in most ViewModel tests.

```kotlin
// test/util/MainDispatcherRule.kt
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

> Note: `TestCoroutineDispatcher` was deprecated in coroutines 1.6 and removed in 1.7. Do not use it.

---

## ViewModel Test Template

```kotlin
// presentation/[feature]/[Feature]ViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class [Feature]ViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val get[Feature]s: Get[Feature]sUseCase = mockk()

    // --- Subject under test ---
    private lateinit var viewModel: [Feature]ViewModel

    @Before
    fun setUp() {
        // Stub with a never-completing flow BEFORE constructing the ViewModel.
        // The ViewModel's init block calls onAction(Load) immediately; without this stub
        // MockK throws MockKException: no answer found for Get[Feature]sUseCase().
        every { get[Feature]s() } returns emptyFlow()
        viewModel = [Feature]ViewModel(get[Feature]s)
    }

    // ── Initial State ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty list isLoading false and no error`() = runTest {
        // Re-stub to a non-completing flow so we can observe the pre-result state
        every { get[Feature]s() } returns flow { delay(Long.MAX_VALUE) }
        viewModel = [Feature]ViewModel(get[Feature]s)  // fresh instance with suspended load

        viewModel.state.test {
            val initial = awaitItem()
            assertThat(initial.items).isEmpty()
            assertThat(initial.isLoading).isTrue()  // Loading is true because init triggered Load
            assertThat(initial.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Happy Path ────────────────────────────────────────────────────────────

    @Test
    fun `Load action when useCase returns data then state updated with items`() = runTest {
        val items = listOf([Feature]("1", "Item A"), [Feature]("2", "Item B"))
        every { get[Feature]s() } returns flow {
            emit(Result.Loading)
            emit(Result.Success(items))
        }

        viewModel.state.test {
            viewModel.onAction([Feature]UiAction.Load)

            val initial = awaitItem()
            assertThat(initial.isLoading).isFalse()

            val loading = awaitItem()
            assertThat(loading.isLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.items).isEqualTo(items)
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.error).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Error Path ────────────────────────────────────────────────────────────

    @Test
    fun `Load action when useCase emits error then ShowError effect sent and loading cleared`() = runTest {
        val exception = IOException("Network unavailable")
        every { get[Feature]s() } returns flow {
            emit(Result.Loading)
            emit(Result.Error(exception))
        }

        viewModel.effect.test {
            viewModel.onAction([Feature]UiAction.Load)

            val effect = awaitItem()
            assertThat(effect).isInstanceOf([Feature]UiEffect.ShowError::class.java)
            assertThat((effect as [Feature]UiEffect.ShowError).message)
                .isEqualTo("Network unavailable")

            cancelAndIgnoreRemainingEvents()
        }

        // Also verify state
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // ── Navigation Effect ─────────────────────────────────────────────────────

    @Test
    fun `Select action sends NavigateToDetail effect with correct id`() = runTest {
        viewModel.effect.test {
            viewModel.onAction([Feature]UiAction.Select(id = "42"))

            val effect = awaitItem()
            assertThat(effect).isEqualTo([Feature]UiEffect.NavigateToDetail("42"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    @Test
    fun `Retry action reloads data after error`() = runTest {
        val items = listOf([Feature]("1", "Item A"))
        every { get[Feature]s() } returns flowOf(Result.Success(items))

        viewModel.onAction([Feature]UiAction.Load)
        viewModel.onAction([Feature]UiAction.Retry)

        // Verify useCase was called twice
        verify(exactly = 2) { get[Feature]s() }
    }
}
```

---

## Use Case Test Template

```kotlin
// domain/usecase/Get[Feature]sUseCaseTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class Get[Feature]sUseCaseTest {

    private val repository: [Feature]Repository = mockk()
    private val useCase = Get[Feature]sUseCase(repository)

    @Test
    fun `invoke emits Loading then Success when repository returns data`() = runTest {
        val items = listOf([Feature]("1", "Item A"))
        every { repository.get[Feature]s() } returns flowOf(items)

        useCase().test {
            assertThat(awaitItem()).isInstanceOf(Result.Loading::class.java)
            val success = awaitItem()
            assertThat(success).isInstanceOf(Result.Success::class.java)
            assertThat((success as Result.Success).data).isEqualTo(items)
            awaitComplete()
        }
    }

    @Test
    fun `invoke emits Error when repository throws`() = runTest {
        val exception = IOException("Timeout")
        every { repository.get[Feature]s() } returns flow { throw exception }

        useCase().test {
            // Loading first
            assertThat(awaitItem()).isInstanceOf(Result.Loading::class.java)
            // Then Error
            val error = awaitItem()
            assertThat(error).isInstanceOf(Result.Error::class.java)
            assertThat((error as Result.Error).exception).isEqualTo(exception)
            awaitComplete()
        }
    }

    @Test
    fun `invoke emits Success with empty list when repository returns empty`() = runTest {
        every { repository.get[Feature]s() } returns flowOf(emptyList())

        useCase().test {
            awaitItem() // Loading
            val success = awaitItem() as Result.Success
            assertThat(success.data).isEmpty()
            awaitComplete()
        }
    }
}
```

---

## Mapper Test Template

```kotlin
// data/mapper/[Feature]MapperTest.kt
class [Feature]MapperTest {

    @Test
    fun `toDomain maps all fields from DTO correctly`() {
        val dto = [Feature]Dto(
            id = "abc-123",
            name = "Widget Pro",
            price = 29.99,
            isAvailable = true
        )

        val domain = dto.toDomain()

        assertThat(domain.id).isEqualTo("abc-123")
        assertThat(domain.name).isEqualTo("Widget Pro")
        assertThat(domain.price).isEqualTo(29.99)
        assertThat(domain.isAvailable).isTrue()
    }

    @Test
    fun `toDomain with null optional price defaults to zero`() {
        val dto = [Feature]Dto(id = "1", name = "Free Item", price = null, isAvailable = true)
        assertThat(dto.toDomain().price).isEqualTo(0.0)
    }

    @Test
    fun `toDomain with null optional name defaults to empty string`() {
        val dto = [Feature]Dto(id = "1", name = null, price = 9.99, isAvailable = true)
        assertThat(dto.toDomain().name).isEmpty()
    }

    @Test
    fun `List toDomain maps every item`() {
        val dtos = listOf(
            [Feature]Dto("1", "A", 1.0, true),
            [Feature]Dto("2", "B", 2.0, false)
        )
        val domains = dtos.toDomain()
        assertThat(domains).hasSize(2)
        assertThat(domains[0].id).isEqualTo("1")
        assertThat(domains[1].id).isEqualTo("2")
    }
}
```

---

## Repository Test Template

```kotlin
// data/repository/[Feature]RepositoryImplTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class [Feature]RepositoryImplTest {

    private val api: [Feature]Api = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository = [Feature]RepositoryImpl(api, testDispatcher)

    @Test
    fun `get[Feature]s emits mapped domain models when API succeeds`() = runTest {
        val dtos = listOf([Feature]Dto("1", "Widget", 9.99, true))
        coEvery { api.get[Feature]s() } returns dtos

        repository.get[Feature]s().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items[0].id).isEqualTo("1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `get[Feature]s propagates exception when API throws`() = runTest {
        coEvery { api.get[Feature]s() } throws IOException("Server error")

        repository.get[Feature]s().test {
            awaitError().let { error ->
                assertThat(error).isInstanceOf(IOException::class.java)
                assertThat(error.message).isEqualTo("Server error")
            }
        }
    }
}
```

---

## Turbine Cheat Sheet

```kotlin
flow.test {
    awaitItem()                          // assert next emission — throws if flow completes/errors first
    awaitComplete()                      // assert flow completed normally
    awaitError()                         // assert flow threw — returns the Throwable
    cancelAndIgnoreRemainingEvents()     // cancel subscription, skip remaining (use to avoid timeout)
    expectNoEvents()                     // assert nothing emitted yet (useful for timing assertions)
    ensureAllEventsConsumed()            // fail if any events were not consumed (strict mode)
}

// With timeout override
flow.test(timeout = 5.seconds) { ... }
```

---

## MockK Cheat Sheet

```kotlin
// Stub
every { mock.synchronousMethod() } returns value
coEvery { mock.suspendMethod() } returns value
every { mock.flowMethod() } returns flowOf(value)
every { mock.methodThatThrows() } throws SomeException()

// Argument matchers
every { mock.method(any()) } returns value
every { mock.method(match { it.id == "123" }) } returns value

// Verify
verify { mock.synchronousMethod() }
coVerify { mock.suspendMethod() }
verify(exactly = 2) { mock.method(any()) }
verify(exactly = 0) { mock.neverCalledMethod() }
confirmVerified(mock)    // fail if any unexpected calls were made

// Spy (partial mock)
val spy = spyk(RealClass())
every { spy.methodToStub() } returns fakeValue
```

---

## Common Test Anti-Patterns to Avoid

```kotlin
// BAD — testing implementation detail (method was called), not behavior (correct state emitted)
verify { getProductsUseCase.invoke() }  // pointless if state wasn't verified

// GOOD — assert observable outcome
viewModel.state.test {
    val state = awaitItem()
    assertThat(state.items).isEqualTo(expected)
}

// BAD — brittle: tests order of emissions without reason
awaitItem(); awaitItem(); awaitItem()  // which one is loading? which is success?

// GOOD — named and purposeful
val loadingState = awaitItem()
assertThat(loadingState.isLoading).isTrue()
val successState = awaitItem()
assertThat(successState.items).isNotEmpty()

// BAD — Thread.sleep() for async timing
Thread.sleep(1000)
assertThat(viewModel.state.value.items).isNotEmpty()

// GOOD — Turbine awaits emissions deterministically
viewModel.state.test { awaitItem() }
```
