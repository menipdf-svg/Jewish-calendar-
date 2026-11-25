package de.jeanlucmakiola.calendula.data.time

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.di.ApplicationScope
import de.jeanlucmakiola.floret.android.broadcastTicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.TimeZone
import javax.inject.Inject

/**
 * The zone the device is in. Every screen that turns wall clock into instants
 * reads it from here, so the timeline, the event form and a saved move use the
 * same zone (#387).
 */
interface ZoneSource {
    /** The device's zone, re-emitted whenever it changes. */
    val zone: Flow<TimeZone>

    /** The device's zone right now. */
    fun current(): TimeZone
}

/** Follows the system's zone broadcast, one receiver shared by every collector. */
class AndroidZoneSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) : ZoneSource {

    override val zone: Flow<TimeZone> = context.broadcastTicks(Intent.ACTION_TIMEZONE_CHANGED)
        .map { current() }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}
