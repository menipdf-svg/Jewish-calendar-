package de.jeanlucmakiola.calendula.data.time

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.di.ApplicationScope
import de.jeanlucmakiola.floret.android.broadcastTicks
import de.jeanlucmakiola.floret.android.stateWhileSubscribed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock

/** The current local date, re-emitted whenever it changes (#373). */
interface TodaySource {
    val today: Flow<LocalDate>
}

/**
 * Re-reads the date on every minute boundary while collected, and at once on the
 * system's date, time and zone broadcasts, which can arrive late or not at all.
 * One receiver and tick loop is shared by every collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTodaySource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) : TodaySource {

    override val today: Flow<LocalDate> = context
        .broadcastTicks(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        .transformLatest {
            while (true) {
                emit(currentDate())
                delay(MINUTE_MILLIS - Clock.System.now().toEpochMilliseconds() % MINUTE_MILLIS)
            }
        }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
}

private const val MINUTE_MILLIS = 60_000L

fun currentDate(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Today as a hot flow seeded with [initial]. [onRollover] runs with the previous
 * and the new date each time the day changes while collected, so a screen left
 * open over midnight can move off yesterday (#373).
 */
fun Flow<LocalDate>.stateFollowingDays(
    scope: CoroutineScope,
    initial: LocalDate,
    onRollover: (old: LocalDate, new: LocalDate) -> Unit,
): StateFlow<LocalDate> {
    var last = initial
    return onEach { date ->
        val old = last
        last = date
        if (old != date) onRollover(old, date)
    }.stateWhileSubscribed(scope, initial)
}
