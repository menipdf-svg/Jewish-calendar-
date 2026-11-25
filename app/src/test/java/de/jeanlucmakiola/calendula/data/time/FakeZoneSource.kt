package de.jeanlucmakiola.calendula.data.time

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone

/** A device zone a test sets, by default the machine's own. */
class FakeZoneSource(zone: TimeZone = TimeZone.currentSystemDefault()) : ZoneSource {
    override val zone: MutableStateFlow<TimeZone> = MutableStateFlow(zone)

    override fun current(): TimeZone = zone.value
}
