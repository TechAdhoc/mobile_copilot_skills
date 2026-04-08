package com.example.app.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit Rule that replaces Dispatchers.Main with a test dispatcher.
 * Required for testing ViewModels that use viewModelScope.
 *
 * Usage:
 *   @get:Rule val mainDispatcherRule = MainDispatcherRule()
 *
 * Use [UnconfinedTestDispatcher] (default) for most ViewModel tests — it runs
 * coroutines eagerly without needing explicit advanceUntilIdle() calls.
 *
 * Use [StandardTestDispatcher] when you need precise control over coroutine
 * scheduling (e.g., testing interleaved emissions or cancellation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
