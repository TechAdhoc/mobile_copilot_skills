package com.example.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

// ── Qualifier Annotations ─────────────────────────────────────────────────────
// Use these to inject the correct dispatcher instead of hardcoding Dispatchers.IO.
// This makes all coroutine code testable — swap real dispatchers for test ones.

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

// ── Module ────────────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    /**
     * For all I/O operations: network, disk, database.
     * Inject with: @IoDispatcher private val dispatcher: CoroutineDispatcher
     */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * For UI-thread work (rarely needed directly — prefer StateFlow + collect).
     */
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * For CPU-intensive work: sorting, filtering, parsing large datasets.
     */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
