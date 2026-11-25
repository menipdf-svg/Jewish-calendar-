package com.menipdf.jewishcalendar

import androidx.compose.ui.graphics.Color
import java.time.LocalDate

enum class Cat(val label: String, val color: Color) {
    SHABBAT("כניסת ויציאת שבת", Color(0xFF8E24AA)),
    PARSHA("פרשת השבוע ושבתות מיוחדות", Color(0xFF3949AB)),
    HOLIDAY("חגים ומועדים", Color(0xFF0B8043)),
    RC("ראש חודש", Color(0xFF0277BD)),
    FAST("צומות", Color(0xFFC62828)),
    OMER("ספירת העומר", Color(0xFF616161)),
    DAF("דף יומי", Color(0xFF00796B)),
    ZMANIM("זמני היום (תצוגת שבוע)", Color(0xFFE65100))
}

/** סדר הצגת אירועי "כל היום" */
val CAT_ORDER = listOf(Cat.HOLIDAY, Cat.PARSHA, Cat.RC, Cat.FAST, Cat.OMER, Cat.DAF)

data class Ev(
    val date: LocalDate,
    val cat: Cat,
    val label: String,
    val full: String,
    val timeMillis: Long? = null,
    val chag: Boolean = false,
    val memo: String = "",
    val key: String = ""
)

data class Zman(
    val id: String,
    val label: String,
    val short: String,
    val desc: String,
    val key: Boolean,
    val timeMillis: Long
)

data class City(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val tz: String,
    val il: Boolean,
    val mins: Int,
    val group: String
)

enum class ViewMode(val label: String, val short: String) {
    MONTH("חודש", "חודש"), WEEK("שבוע", "שבוע"), DAY("יום", "יום"), AGENDA("לוח זמנים", "רשימה")
}

val CITIES = listOf(
    City("jerusalem", "ירושלים", 31.7683, 35.2137, "Asia/Jerusalem", true, 40, "ישראל"),
    City("telaviv", "תל אביב-יפו", 32.0853, 34.7818, "Asia/Jerusalem", true, 20, "ישראל"),
    City("haifa", "חיפה", 32.794, 34.9896, "Asia/Jerusalem", true, 30, "ישראל"),
    City("beersheva", "באר שבע", 31.2518, 34.7913, "Asia/Jerusalem", true, 20, "ישראל"),
    City("bneibrak", "בני ברק", 32.0809, 34.8338, "Asia/Jerusalem", true, 20, "ישראל"),
    City("bethshemesh", "בית שמש", 31.7469, 34.9881, "Asia/Jerusalem", true, 30, "ישראל"),
    City("ashdod", "אשדוד", 31.8044, 34.6553, "Asia/Jerusalem", true, 20, "ישראל"),
    City("netanya", "נתניה", 32.3215, 34.8532, "Asia/Jerusalem", true, 20, "ישראל"),
    City("petahtikva", "פתח תקווה", 32.0841, 34.8878, "Asia/Jerusalem", true, 20, "ישראל"),
    City("rishon", "ראשון לציון", 31.973, 34.7925, "Asia/Jerusalem", true, 20, "ישראל"),
    City("safed", "צפת", 32.9646, 35.496, "Asia/Jerusalem", true, 30, "ישראל"),
    City("tiberias", "טבריה", 32.7922, 35.5312, "Asia/Jerusalem", true, 30, "ישראל"),
    City("eilat", "אילת", 29.5577, 34.9519, "Asia/Jerusalem", true, 20, "ישראל"),
    City("newyork", "ניו יורק", 40.7128, -74.006, "America/New_York", false, 18, "חוץ לארץ"),
    City("losangeles", "לוס אנג׳לס", 34.0522, -118.2437, "America/Los_Angeles", false, 18, "חוץ לארץ"),
    City("miami", "מיאמי", 25.7617, -80.1918, "America/New_York", false, 18, "חוץ לארץ"),
    City("toronto", "טורונטו", 43.6532, -79.3832, "America/Toronto", false, 18, "חוץ לארץ"),
    City("montreal", "מונטריאול", 45.5017, -73.5673, "America/Toronto", false, 18, "חוץ לארץ"),
    City("london", "לונדון", 51.5074, -0.1278, "Europe/London", false, 18, "חוץ לארץ"),
    City("paris", "פריז", 48.8566, 2.3522, "Europe/Paris", false, 18, "חוץ לארץ"),
    City("antwerp", "אנטוורפן", 51.2194, 4.4025, "Europe/Brussels", false, 18, "חוץ לארץ"),
    City("buenosaires", "בואנוס איירס", -34.6037, -58.3816, "America/Argentina/Buenos_Aires", false, 18, "חוץ לארץ"),
    City("melbourne", "מלבורן", -37.8136, 144.9631, "Australia/Melbourne", false, 18, "חוץ לארץ")
)

private val WD_FULL = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
val WD_LETTER = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
val GREG_MONTHS = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
)

/** ראשון = 0 ... שבת = 6 */
fun LocalDate.wd(): Int = dayOfWeek.value % 7
fun LocalDate.weekdayName(): String = "יום " + WD_FULL[wd()]
fun LocalDate.startOfWeek(): LocalDate = minusDays(wd().toLong())
fun LocalDate.longDate(): String = "$dayOfMonth ב${GREG_MONTHS[monthValue - 1]} $year"
fun LocalDate.monthYear(): String = "${GREG_MONTHS[monthValue - 1]} $year"
