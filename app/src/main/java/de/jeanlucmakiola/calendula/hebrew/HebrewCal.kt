package de.jeanlucmakiola.calendula.hebrew

import android.content.Context
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Instant

/** מצב גלובלי קל של הלוח העברי: עיר נבחרת, מנוע, והזרקת אירועים לזרם האירועים. */
object HebrewCal {
    const val CALENDAR_ID = -1L

    private var prefs: android.content.SharedPreferences? = null
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    @Volatile
    var engine: HebrewEngine = HebrewEngine(HEB_CITIES.first(), HEB_CITIES.first().mins)
        private set

    @Volatile
    var enabled: Boolean = true
        private set

    val cityId: String get() = engine.city.id

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("hebrew_cal", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", true)
        apply(p.getString("city", null) ?: HEB_CITIES.first().id, bump = false)
    }

    fun setCity(id: String) {
        prefs?.edit()?.putString("city", id)?.apply()
        apply(id, bump = true)
    }

    fun setEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
        enabled = value
        _version.value++
    }

    private fun apply(id: String, bump: Boolean) {
        val city = HEB_CITIES.firstOrNull { it.id == id } ?: HEB_CITIES.first()
        engine = HebrewEngine(city, city.mins)
        if (bump) _version.value++
    }

    fun isSynthetic(e: EventInstance): Boolean = e.calendarId == CALENDAR_ID

    /** חגים, פרשה, צומות וראש חודש – לתצוגות חודש/שבוע/רשימה (בלי זמני שבת). */
    fun instances(range: ClosedRange<Instant>): List<EventInstance> = build(range, times = false)

    /** כניסת שבת/יציאתה – מוצגים רק בתצוגת יום, כשנכנסים לתאריך. */
    fun timeInstances(range: ClosedRange<Instant>): List<EventInstance> = build(range, times = true)

    private fun build(range: ClosedRange<Instant>, times: Boolean): List<EventInstance> {
        if (!enabled) return emptyList()
        val eng = engine
        val first = LocalDate.ofEpochDay(Math.floorDiv(range.start.toEpochMilliseconds(), DAY_MS) - 1)
        val last = LocalDate.ofEpochDay(Math.floorDiv(range.endInclusive.toEpochMilliseconds(), DAY_MS) + 1)
        val out = ArrayList<EventInstance>()
        var d = first
        while (!d.isAfter(last)) {
            val startMs = d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val endMs = startMs + DAY_MS
            if (endMs > range.start.toEpochMilliseconds() && startMs <= range.endInclusive.toEpochMilliseconds()) {
                eng.events(d).forEachIndexed { i, ev ->
                    if ((ev.cat == HebCat.SHABBAT) != times) return@forEachIndexed
                    val id = -(d.toEpochDay() * 16 + i) - 1000L
                    out += EventInstance(
                        instanceId = id,
                        eventId = id,
                        calendarId = CALENDAR_ID,
                        title = ev.title,
                        start = Instant.fromEpochMilliseconds(startMs),
                        end = Instant.fromEpochMilliseconds(endMs),
                        isAllDay = true,
                        color = ev.cat.argb,
                        location = null,
                    )
                }
            }
            d = d.plusDays(1)
        }
        return out
    }

    private const val DAY_MS = 86_400_000L
}
