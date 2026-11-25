package de.jeanlucmakiola.calendula.hebrew

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class HebCat(val argb: Int) {
    SHABBAT(0xFF8E24AA.toInt()),
    PARSHA(0xFF3949AB.toInt()),
    HOLIDAY(0xFF0B8043.toInt()),
    ROSH_CHODESH(0xFF0277BD.toInt()),
    FAST(0xFFC62828.toInt()),
}

data class HebEvent(val date: LocalDate, val cat: HebCat, val title: String, val key: String = "")

/** תאריך עברי, חגים, פרשה וזמני כניסת שבת/יציאתה – מבוסס KosherJava Zmanim. */
class HebrewEngine(val city: HebCity, private val candleMins: Int, private val havdalah: String = "8.5") {
    private val tz: TimeZone = TimeZone.getTimeZone(city.tz)
    val zone: ZoneId = tz.toZoneId()
    private val geo = GeoLocation(city.name, city.lat, city.lon, 0.0, tz)
    private val fmt = HebrewDateFormatter().apply { setHebrewFormat(true) }
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val cache = HashMap<LocalDate, List<HebEvent>>()

    private fun hm(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun cal(d: LocalDate): Calendar = Calendar.getInstance(tz).apply {
        clear()
        set(d.year, d.monthValue - 1, d.dayOfMonth, 12, 0, 0)
    }

    private fun jc(d: LocalDate): JewishCalendar = JewishCalendar(cal(d)).also {
        it.setInIsrael(city.il)
        it.setUseModernHolidays(true)
    }

    private fun zc(d: LocalDate): ComplexZmanimCalendar = ComplexZmanimCalendar(geo).also {
        it.setCalendar(cal(d))
        it.setCandleLightingOffset(candleMins.toDouble())
    }

    /** היום בחודש העברי באותיות ("ט״ו"). */
    fun hebDay(d: LocalDate): String = fmt.formatHebrewNumber(jc(d).getJewishDayOfMonth())

    /** "ט״ו כסלו תשפ״ז". */
    fun hebFull(d: LocalDate): String = fmt.format(jc(d))

    fun events(d: LocalDate): List<HebEvent> = synchronized(cache) { cache.getOrPut(d) { build(d) } }

    private fun build(d: LocalDate): List<HebEvent> {
        val out = ArrayList<HebEvent>()
        val j = jc(d)
        val dow = d.dayOfWeek

        if (dow == DayOfWeek.SATURDAY) {
            val p = safe { fmt.formatParsha(j) }
            if (p.isNotBlank()) out += HebEvent(d, HebCat.PARSHA, "פרשת $p")
            val sp = safe { fmt.formatSpecialParsha(j) }
            if (sp.isNotBlank()) out += HebEvent(d, HebCat.PARSHA, "שבת $sp")
        }

        val yt = safe { fmt.formatYomTov(j) }
        if (yt.isNotBlank() && !yt.startsWith("ראש חודש")) {
            val fast = j.isTaanis() && !j.isYomTov()
            val label = if (j.isChanukah()) "$yt: נר ${fmt.formatHebrewNumber(j.getDayOfChanukah())}" else yt
            out += HebEvent(d, if (fast) HebCat.FAST else HebCat.HOLIDAY, label)
        }

        if (j.isRoshChodesh()) {
            val month = if (j.getJewishDayOfMonth() == 30) fmt.formatMonth(jc(d.plusDays(1))) else fmt.formatMonth(j)
            out += HebEvent(d, HebCat.ROSH_CHODESH, "ראש חודש $month")
        }

        val z = zc(d)
        val assurToday = j.isAssurBemelacha()
        val assurTomorrow = jc(d.plusDays(1)).isAssurBemelacha()
        if (!assurToday && assurTomorrow) {
            z.getCandleLighting()?.let {
                val label = if (dow == DayOfWeek.FRIDAY) "כניסת שבת" else "הדלקת נרות"
                out += HebEvent(d, HebCat.SHABBAT, "$label ${hm(it.time)}", "candle")
            }
        } else if (assurToday && assurTomorrow) {
            val t = if (dow == DayOfWeek.FRIDAY) z.getCandleLighting() else tzeit(z)
            t?.let {
                val label = if (dow == DayOfWeek.FRIDAY) "כניסת שבת" else "הדלקת נרות"
                out += HebEvent(d, HebCat.SHABBAT, "$label ${hm(it.time)}", "candle")
            }
        }
        if (assurToday && !assurTomorrow) {
            tzeit(z)?.let {
                val label = if (dow == DayOfWeek.SATURDAY) "צאת שבת" else "צאת החג"
                out += HebEvent(d, HebCat.SHABBAT, "$label ${hm(it.time)}", "havdalah")
            }
        }
        return out
    }

    private fun tzeit(z: ComplexZmanimCalendar): java.util.Date? = when (havdalah) {
        "8.5" -> z.getTzais()
        "72" -> z.getTzais72()
        else -> z.getSunset()?.let { java.util.Date(it.time + havdalah.toLong() * 60_000L) }
    }

    private inline fun safe(block: () -> String?): String = try { block() ?: "" } catch (e: Exception) { "" }
}
