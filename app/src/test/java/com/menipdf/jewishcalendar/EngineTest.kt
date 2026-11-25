package com.menipdf.jewishcalendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * בדיקות מנוע החישוב (ללא ממשק): תאריך עברי, חגים, ערב חג, שבת וזמני כניסה/יציאה.
 * ערכי הייחוס לזמנים הם ערכי Hebcal שחושבו עבור ירושלים (40 דק׳, 8.5°).
 */
class EngineTest {
    private fun eng(id: String = "jerusalem", mins: Int = 40, hav: String = "8.5") =
        Engine(CITIES.first { it.id == id }, mins, hav)

    private fun labels(e: Engine, y: Int, m: Int, d: Int) = e.events(LocalDate.of(y, m, d)).map { it.label }

    private fun min(hm: String): Int = hm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

    private fun assertTimeNear(expected: String, actualMs: Long?, e: Engine, what: String) {
        val actual = min(e.hm(requireNotNull(actualMs) { "$what: אין זמן" }))
        assertTrue("$what: צפוי $expected בפועל ${e.hm(actualMs)}", Math.abs(actual - min(expected)) <= 1)
    }

    @Test
    fun hebrewDate_isCorrect() {
        val e = eng()
        assertEquals("ט״ו כסלו תשפ״ז", e.hebFull(LocalDate.of(2026, 11, 25)))
        assertEquals("כסלו", e.hebMonth(LocalDate.of(2026, 11, 25)))
        assertEquals(15, e.hebDayOfMonth(LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun roshHashana_diaspora_twoDays_israel_oneDay() {
        val dia = eng("newyork", 18)
        assertTrue("${labels(dia, 2026, 9, 12)}", labels(dia, 2026, 9, 12).any { it.contains("ראש השנה") })
        assertTrue(labels(dia, 2026, 9, 13).any { it.contains("ראש השנה") })
        val il = eng()
        assertTrue(labels(il, 2026, 9, 12).any { it.contains("ראש השנה") })
        assertFalse(labels(il, 2026, 9, 13).any { it.contains("ראש השנה") })
    }

    @Test
    fun yomKippur_and_eves() {
        val e = eng()
        assertTrue(labels(e, 2026, 9, 21).any { it.contains("יום כיפור") })
        assertTrue("ערב יום כיפור", labels(e, 2026, 9, 20).any { it.contains("ערב") })
        assertTrue("ערב ראש השנה", labels(e, 2026, 9, 11).any { it.contains("ערב") })
    }

    @Test
    fun sukkot_cholHamoed_and_endOfHoliday() {
        val e = eng("newyork", 18)
        assertTrue(labels(e, 2026, 9, 26).any { it.contains("סוכות") })
        assertTrue(labels(e, 2026, 9, 28).any { it.contains("חול המועד") })
        assertTrue(labels(e, 2026, 10, 2).any { it.contains("הושענא רבה") })
        assertTrue(labels(e, 2026, 10, 3).any { it.contains("שמיני עצרת") })
        assertTrue(labels(e, 2026, 10, 4).any { it.contains("שמחת תורה") })
    }

    @Test
    fun shabbat_parsha_and_times_match_hebcal() {
        val e = eng()
        assertTrue(labels(e, 2026, 11, 28).any { it == "פרשת וישלח" })
        val fri = e.events(LocalDate.of(2026, 11, 27)).first { it.key == "candle" }
        assertEquals("כניסת שבת", fri.label)
        assertTimeNear("15:55", fri.timeMillis, e, "כניסת שבת 27.11")
        val sat = e.events(LocalDate.of(2026, 11, 28)).first { it.key == "havdalah" }
        assertEquals("צאת שבת", sat.label)
        assertTimeNear("17:14", sat.timeMillis, e, "צאת שבת 28.11")
    }

    @Test
    fun shabbat_times_multipleWeeks() {
        val e = eng()
        val expected = listOf(
            Triple(LocalDate.of(2026, 11, 6), "16:05", "17:23"),
            Triple(LocalDate.of(2026, 11, 13), "16:00", "17:18"),
            Triple(LocalDate.of(2026, 11, 20), "15:57", "17:16"),
            Triple(LocalDate.of(2026, 12, 4), "15:54", "17:14")
        )
        for ((fri, candle, havdalah) in expected) {
            assertTimeNear(candle, e.events(fri).first { it.key == "candle" }.timeMillis, e, "כניסה $fri")
            assertTimeNear(havdalah, e.events(fri.plusDays(1)).first { it.key == "havdalah" }.timeMillis, e, "יציאה $fri")
        }
    }

    @Test
    fun noShabbatTimes_onWeekdays() {
        val e = eng()
        assertTrue(e.events(LocalDate.of(2026, 11, 25)).none { it.key == "candle" || it.key == "havdalah" })
    }

    @Test
    fun roshChodesh_and_chanukah() {
        val e = eng()
        assertTrue("${labels(e, 2026, 12, 11)}", labels(e, 2026, 12, 11).any { it.startsWith("ראש חודש") })
        assertTrue(labels(e, 2026, 12, 4).any { it.contains("חנוכה") })
    }

    @Test
    fun zmanim_areOrdered() {
        val z = eng().zmanim(LocalDate.of(2026, 11, 25))
        val ids = z.map { it.id }
        assertTrue(ids.containsAll(listOf("sunrise", "chatzot", "sunset", "tzeit")))
        val byId = z.associateBy { it.id }
        assertTrue(byId.getValue("sunrise").timeMillis < byId.getValue("chatzot").timeMillis)
        assertTrue(byId.getValue("chatzot").timeMillis < byId.getValue("sunset").timeMillis)
        assertTrue(byId.getValue("sunset").timeMillis < byId.getValue("tzeit").timeMillis)
    }

    @Test
    fun candleMinutes_changeCandleTime() {
        val a = eng(mins = 18).events(LocalDate.of(2026, 11, 27)).first { it.key == "candle" }.timeMillis!!
        val b = eng(mins = 40).events(LocalDate.of(2026, 11, 27)).first { it.key == "candle" }.timeMillis!!
        assertEquals(22L * 60_000L, a - b)
    }
}
