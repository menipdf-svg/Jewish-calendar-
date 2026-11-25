package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class PageStateCacheTest {

    private var running = 0

    private fun TestScope.trackingCache() =
        PageStateCache<Int, Int>(backgroundScope, -1, { a, b -> abs(a - b) <= 1 }) { key ->
            flow {
                running++
                try {
                    emit(key)
                    awaitCancellation()
                } finally {
                    running--
                }
            }
        }

    @Test
    fun `the same key shares one state`() = runTest {
        val cache = trackingCache()
        assertThat(cache.get(5)).isSameInstanceAs(cache.get(5))
    }

    @Test
    fun `nearby entries are kept`() = runTest {
        val cache = PageStateCache<Int, Int>(backgroundScope, 0, { a, b -> abs(a - b) <= 2 }) { flow { emit(it) } }
        cache.get(4)
        cache.get(5)
        cache.get(6)
        assertThat(cache.size).isEqualTo(3)
    }

    @Test
    fun `an entry loads only while collected`() = runTest(UnconfinedTestDispatcher()) {
        val cache = trackingCache()
        val state = cache.get(0)
        assertThat(running).isEqualTo(0)

        val page = backgroundScope.launch { state.collect {} }
        assertThat(running).isEqualTo(1)
        assertThat(state.value).isEqualTo(0)

        page.cancel()
        advanceTimeBy(5_001)
        assertThat(running).isEqualTo(0)
    }

    @Test
    fun `an unwatched entry far away is forgotten`() = runTest(UnconfinedTestDispatcher()) {
        val cache = trackingCache()
        cache.get(0)
        cache.get(10)
        assertThat(cache.size).isEqualTo(1)
    }

    @Test
    fun `an entry a page still collects is kept however far away`() = runTest(UnconfinedTestDispatcher()) {
        val cache = trackingCache()
        backgroundScope.launch { cache.get(0).collect {} }

        cache.get(10)
        assertThat(running).isEqualTo(1)
        assertThat(cache.size).isEqualTo(2)
    }

    @Test
    fun `a forgotten entry a page holds still loads once collected`() = runTest(UnconfinedTestDispatcher()) {
        val cache = trackingCache()
        // The page took its state but hasn't started collecting when 0 is let go.
        val held = cache.get(0)
        cache.get(10)

        val values = mutableListOf<Int>()
        backgroundScope.launch { held.collect { values += it } }
        assertThat(running).isEqualTo(1)
        assertThat(values.last()).isEqualTo(0)
    }

    @Test
    fun `an entry restarts after the screen comes back`() = runTest(UnconfinedTestDispatcher()) {
        val cache = trackingCache()
        val held = cache.get(0)
        backgroundScope.launch { held.collect {} }.cancel()
        advanceTimeBy(5_001)
        cache.get(10)
        assertThat(running).isEqualTo(0)

        backgroundScope.launch { held.collect {} }
        assertThat(running).isEqualTo(1)
    }
}
