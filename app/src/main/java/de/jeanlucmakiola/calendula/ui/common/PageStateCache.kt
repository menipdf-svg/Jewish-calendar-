package de.jeanlucmakiola.calendula.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One shared state per pager page — a week, a day, a month — so a page and the
 * screen's anchor read the same query, and the pages either side stay loaded
 * across a swipe.
 *
 * An entry queries only while something collects it, stopping [STOP_TIMEOUT_MILLIS]
 * after the last collector leaves, and starts again when one comes back. Letting
 * an entry go only forgets it: a page still holding it keeps a working state
 * however far the pager composes or prefetches, and however long the screen sat
 * stopped (#440, #454). A forgotten entry nobody collects holds no coroutine, so
 * paging through a year doesn't keep a year of queries around. Main thread only.
 *
 * @param keep whether a cached key is still worth holding once [get] asks for another.
 */
class PageStateCache<K, S>(
    private val scope: CoroutineScope,
    private val initial: S,
    private val keep: (cached: K, requested: K) -> Boolean,
    private val load: (K) -> Flow<S>,
) {
    private val entries = HashMap<K, Entry>()

    /** The number of entries held, for tests. */
    internal val size: Int get() = entries.size

    fun get(key: K): StateFlow<S> {
        // One still collected stays, so the anchor asking for it again shares its query.
        entries.entries.removeAll { (cached, entry) ->
            cached != key && !keep(cached, key) && !entry.isCollected
        }
        return entries.getOrPut(key) { Entry(key) }
    }

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private inner class Entry(private val key: K) : StateFlow<S> {
        private val state = MutableStateFlow(initial)
        private var collectors = 0
        private var loading: Job? = null
        private var stopping: Job? = null

        val isCollected: Boolean get() = collectors > 0

        override val value: S get() = state.value
        override val replayCache: List<S> get() = state.replayCache

        override suspend fun collect(collector: FlowCollector<S>): Nothing {
            collectors++
            stopping?.cancel()
            stopping = null
            if (loading?.isActive != true) {
                loading = scope.launch { load(key).collect { state.value = it } }
            }
            try {
                state.collect(collector)
            } finally {
                if (--collectors == 0) {
                    stopping = scope.launch {
                        delay(STOP_TIMEOUT_MILLIS)
                        loading?.cancel()
                        loading = null
                    }
                }
            }
        }
    }

    private companion object {
        /** Long enough to ride out a configuration change, as `stateWhileSubscribed` does. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
