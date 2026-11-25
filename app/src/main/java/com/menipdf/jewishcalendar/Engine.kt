package com.menipdf.jewishcalendar

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.YomiCalculator
import com.kosherjava.zmanim.util.GeoLocation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * מנוע החישוב: תאריך עברי, חגים, פרשה, זמני היום, הדלקת נרות והבדלה.
 * מבוסס על ספריית KosherJava Zmanim.
 */
class Engine(val city: City, private val candleMins: Int, private val havdalah: String) {

    private val tz: TimeZone = TimeZone.getTimeZone(city.tz)
    val zone: ZoneId = tz.toZoneId()
    private val geo = GeoLocation(city.name, city.lat, city.lon, 0.0, tz)
    private val fmt = HebrewDateFormatter().apply { setHebrewFormat(true) }
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    private val evCache = HashMap<LocalDate, List<Ev>>()
    private val zCache = HashMap<LocalDate, List<Zman>>()

    /* ---------- זמן ---------- */
    fun hm(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun minutesOfDay(ms: Long): Int {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return t.hour * 60 + t.minute
    }

    private fun cal(d: LocalDate): Calendar = Calendar.getInstance(tz).apply {
        clear()
        set(d.year, d.monthValue - 1, d.dayOfMonth, 12, 0, 0)
    }

    private fun jc(d: LocalDate): JewishCalendar {
        val j = JewishCalendar(cal(d))
        j.setInIsrael(city.il)
        j.setUseModernHolidays(true)
        return j
    }

    private fun zc(d: LocalDate): ComplexZmanimCalendar {
        val z = ComplexZmanimCalendar(geo)
        z.setCalendar(cal(d))
        z.setCandleLightingOffset(candleMins.toDouble())
        return z
    }

    private fun tzeit(z: ComplexZmanimCalendar): Date? = when (havdalah) {
        "8.5" -> z.getTzais()
        "72" -> z.getTzais72()
        else -> z.getSunset()?.let { Date(it.time + havdalah.toLong() * 60_000L) }
    }

    /* ---------- תאריך עברי ---------- */
    fun hebFull(d: LocalDate): String = fmt.format(jc(d))
    fun hebDay(d: LocalDate): String = fmt.formatHebrewNumber(jc(d).getJewishDayOfMonth())
    fun hebMonth(d: LocalDate): String = fmt.formatMonth(jc(d))
    fun hebYear(d: LocalDate): String = fmt.formatHebrewNumber(jc(d).getJewishYear())
    fun hebDayOfMonth(d: LocalDate): Int = jc(d).getJewishDayOfMonth()

    /** "חשוון – כסלו תשפ״ז" */
    fun hebRange(first: LocalDate, last: LocalDate): String {
        val ma = hebMonth(first)
        val mb = hebMonth(last)
        val ya = hebYear(first)
        val yb = hebYear(last)
        return when {
            ma == mb && ya == yb -> "$ma $ya"
            ya == yb -> "$ma – $mb $ya"
            else -> "$ma $ya – $mb $yb"
        }
    }

    /* ---------- אירועים ---------- */
    fun events(d: LocalDate): List<Ev> = evCache.getOrPut(d) { build(d) }

    fun isChag(d: LocalDate): Boolean = events(d).any { it.chag }

    /** אירועים אחרי סינון: (כל היום, עם שעה) */
    fun items(d: LocalDate, enabled: (Cat) -> Boolean): Pair<List<Ev>, List<Ev>> {
        val list = events(d).filter { enabled(it.cat) }
        val all = list.filter { it.timeMillis == null }.sortedBy { CAT_ORDER.indexOf(it.cat) }
        val timed = list.filter { it.timeMillis != null }.sortedBy { it.timeMillis }
        return all to timed
    }

    private fun build(d: LocalDate): List<Ev> {
        val out = ArrayList<Ev>()
        val j = jc(d)
        val dow = d.dayOfWeek

        // פרשת השבוע ושבתות מיוחדות
        if (dow == DayOfWeek.SATURDAY) {
            val p = safe { fmt.formatParsha(j) }
            if (p.isNotBlank()) out += Ev(d, Cat.PARSHA, "פרשת $p", "פרשת $p")
            val sp = safe { fmt.formatSpecialParsha(j) }
            if (sp.isNotBlank()) out += Ev(d, Cat.PARSHA, "שבת $sp", "שבת $sp")
        }

        // חגים, מועדים וצומות
        val yt = safe { fmt.formatYomTov(j) }
        if (yt.isNotBlank() && !yt.startsWith("ראש חודש")) {
            val fast = j.isTaanis() && !j.isYomTov()
            var label = yt
            if (j.isChanukah()) label = "$yt: נר ${fmt.formatHebrewNumber(j.getDayOfChanukah())}"
            out += Ev(
                d, if (fast) Cat.FAST else Cat.HOLIDAY, label, label,
                chag = j.isYomTov()
            )
        }

        // ראש חודש
        if (j.isRoshChodesh()) {
            val monthName = if (j.getJewishDayOfMonth() == 30) hebMonth(d.plusDays(1)) else fmt.formatMonth(j)
            out += Ev(d, Cat.RC, "ראש חודש $monthName", "ראש חודש $monthName")
        }

        // ספירת העומר
        val omer = j.getDayOfOmer()
        if (omer > 0) {
            out += Ev(d, Cat.OMER, "${fmt.formatHebrewNumber(omer)} בעומר", "יום $omer בעומר")
        }

        // דף יומי
        try {
            val daf = YomiCalculator.getDafYomiBavli(j)
            if (daf != null) {
                val s = fmt.formatDafYomiBavli(daf)
                out += Ev(d, Cat.DAF, "דף $s", "דף יומי: $s")
            }
        } catch (e: Exception) {
            // תאריך מחוץ לטווח
        }

        // הדלקת נרות והבדלה
        val z = zc(d)
        val assurToday = j.isAssurBemelacha()
        val assurTomorrow = jc(d.plusDays(1)).isAssurBemelacha()
        if (!assurToday && assurTomorrow) {
            z.getCandleLighting()?.let {
                val label = if (dow == DayOfWeek.FRIDAY) "כניסת שבת" else "הדלקת נרות"
                out += Ev(d, Cat.SHABBAT, label, "$label: ${hm(it.time)}", it.time, key = "candle")
            }
        } else if (assurToday && assurTomorrow) {
            // ליל יום טוב שני / ערב שבת שחל ביום טוב
            val t = if (dow == DayOfWeek.FRIDAY) z.getCandleLighting() else tzeit(z)
            t?.let {
                val label = if (dow == DayOfWeek.FRIDAY) "כניסת שבת" else "הדלקת נרות (אחרי צאת הכוכבים)"
                out += Ev(d, Cat.SHABBAT, label, "$label: ${hm(it.time)}", it.time, key = "candle")
            }
        }
        if (assurToday && !assurTomorrow) {
            tzeit(z)?.let {
                val label = if (dow == DayOfWeek.SATURDAY) "צאת שבת" else "צאת החג"
                out += Ev(d, Cat.SHABBAT, label, "$label: ${hm(it.time)}", it.time, key = "havdalah")
            }
        }
        return out
    }

    private inline fun safe(block: () -> String?): String = try {
        block() ?: ""
    } catch (e: Exception) {
        ""
    }

    /* ---------- זמני היום ---------- */
    fun zmanim(d: LocalDate): List<Zman> = zCache.getOrPut(d) {
        val z = zc(d)
        val out = ArrayList<Zman>()
        fun add(id: String, label: String, short: String, desc: String, key: Boolean, t: Date?) {
            if (t != null) out += Zman(id, label, short, desc, key, t.time)
        }
        add("alot", "עלות השחר", "עלות השחר",
            "תחילת היום ההלכתי – מכאן אפשר להתפלל שחרית בדיעבד, ללבוש טלית וכד׳ לפי הפוסקים.", false, z.getAlos16Point1Degrees())
        add("misheyakir", "משיכיר", "משיכיר",
            "הזמן המוקדם להנחת טלית ותפילין ולקריאת שמע של שחרית – כשיש די אור להכיר חבר.", false, z.getMisheyakir11Point5Degrees())
        add("sunrise", "הנץ החמה", "הנץ החמה", "זריחת השמש.", true, z.getSunrise())
        add("shmaMGA", "סוף זמן ק״ש (מג״א)", "ק״ש מג״א",
            "סוף זמן קריאת שמע לפי שיטת המגן אברהם.", false, z.getSofZmanShmaMGA())
        add("shma", "סוף זמן ק״ש (גר״א)", "סוף ק״ש",
            "סוף זמן קריאת שמע לפי הגר״א – שלוש שעות זמניות מהנץ החמה.", true, z.getSofZmanShmaGRA())
        add("tfillaMGA", "סוף זמן תפילה (מג״א)", "תפילה מג״א",
            "סוף זמן תפילת שחרית לפי שיטת המגן אברהם.", false, z.getSofZmanTfilaMGA())
        add("tfilla", "סוף זמן תפילה (גר״א)", "סוף תפילה",
            "סוף זמן תפילת שחרית לפי הגר״א – ארבע שעות זמניות מהנץ החמה.", true, z.getSofZmanTfilaGRA())
        add("chatzot", "חצות היום", "חצות", "אמצע היום, בין הנץ לשקיעה.", true, z.getChatzos())
        add("gedola", "מנחה גדולה", "מנחה ג׳",
            "תחילת הזמן להתפלל מנחה – חצי שעה זמנית אחרי חצות.", true, z.getMinchaGedola())
        add("ketana", "מנחה קטנה", "מנחה ק׳",
            "תשע שעות וחצי זמניות מהנץ – זמן המנחה המובחר.", false, z.getMinchaKetana())
        add("plag", "פלג המנחה", "פלג",
            "שעה ורבע זמנית לפני סוף היום. מכאן אפשר להדליק נרות שבת ולקבל שבת לפי חלק מהמנהגים.", false, z.getPlagHamincha())
        add("sunset", "שקיעה", "שקיעה", "שקיעת השמש.", true, z.getSunset())
        add("tzeit", "צאת הכוכבים", "צאת הכוכבים",
            "תחילת הלילה – מוצאי שבת וחג, זמן קריאת שמע של ערבית.", true, tzeit(z))
        out
    }

    /* ---------- כרטיס שבת ---------- */
    data class NextShabbat(val candle: Ev?, val havdalah: Ev?, val parsha: Ev?)

    fun nextShabbat(from: LocalDate, nowMs: Long): NextShabbat {
        val list = ArrayList<Ev>()
        for (i in 0..14) list += events(from.plusDays(i.toLong()))
        val candle = list.firstOrNull { it.key == "candle" && (it.timeMillis ?: 0) > nowMs }
        val havd = candle?.let { c -> list.firstOrNull { it.key == "havdalah" && (it.timeMillis ?: 0) > (c.timeMillis ?: 0) } }
        val parsha = list.firstOrNull {
            it.cat == Cat.PARSHA && it.label.startsWith("פרשת") && (candle == null || !it.date.isBefore(candle.date))
        }
        return NextShabbat(candle, havd, parsha)
    }
}
