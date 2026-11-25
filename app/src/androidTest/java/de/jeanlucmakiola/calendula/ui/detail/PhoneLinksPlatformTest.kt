package de.jeanlucmakiola.calendula.ui.detail

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The platform phone matcher on real text. It reads the region off the SIM,
 * so these stick to international numbers and to text that must never match
 * anywhere — domestic formats depend on the device they run on.
 */
@RunWith(AndroidJUnit4::class)
class PhoneLinksPlatformTest {

    private fun numbers(text: String) = findPhoneLinks(text).map { it.number }

    @Test
    fun internationalNumbers_areFound() {
        assertThat(numbers("Call +49 30 1234567 or +1 646-558-8656"))
            .containsExactly("+49301234567", "+16465588656")
            .inOrder()
    }

    @Test
    fun trunkZero_isDroppedWithOrWithoutSpace() {
        assertThat(numbers("+49 (0)151 2345678")).containsExactly("+491512345678")
        assertThat(numbers("+49 (0) 151 2345678")).containsExactly("+491512345678")
    }

    @Test
    fun neighbouringNumbers_doNotJoinTheNumber() {
        assertThat(numbers("Start 10:00 +49 151 2345678")).containsExactly("+491512345678")
        assertThat(numbers("Call +49 151 2345678 2 times")).containsExactly("+491512345678")
    }

    @Test
    fun conferenceDialIn_keepsMeetingId() {
        assertThat(numbers("One tap mobile: +16465588656,,85012345678# US"))
            .containsExactly("+16465588656,,85012345678#")
    }

    @Test
    fun meetingIds_areNotNumbers() {
        assertThat(numbers("Meeting ID: 850 1234 5678")).isEmpty()
        assertThat(numbers("Conference ID: 123 456 789#")).isEmpty()
    }

    @Test
    fun datesAndTimes_areNotNumbers() {
        assertThat(numbers("2026-10-14 10:00")).isEmpty()
        assertThat(numbers("Am 14.10.2026 10:00 Uhr")).isEmpty()
        assertThat(numbers("14.10.26 - 16.10.26")).isEmpty()
        assertThat(numbers("10:00 - 11:30")).isEmpty()
        assertThat(numbers("Schuljahr 2025/2026, Season 2025-2026")).isEmpty()
        assertThat(numbers("1.10.2026 - 3.10.2026")).isEmpty()
    }

    @Test
    fun amounts_areNotNumbers() {
        assertThat(numbers("Budget 1.250.000 EUR, 1 250 000 EUR")).isEmpty()
    }

    @Test
    fun isbn_isNotANumber() {
        assertThat(numbers("ISBN 978-3-16-148410-0")).isEmpty()
    }

    @Test
    fun extension_becomesAPause() {
        assertThat(numbers("+1 212 555 1234 ext. 567")).containsExactly("+12125551234,567")
    }

    @Test
    fun webexAccessCode_isNotANumber() {
        assertThat(numbers("Meeting number (access code): 2732 123 4567")).isEmpty()
    }

    @Test
    fun commaSeparatedNumbers_stayApart() {
        assertThat(numbers("+49 30 1234567,+49 151 2345678"))
            .containsExactly("+49301234567", "+491512345678")
            .inOrder()
    }

    @Test
    fun dottedTimes_areNotNumbers() {
        assertThat(numbers("Beginn 9.30 – 12.00 Uhr")).isEmpty()
        assertThat(numbers("10.00-11.30")).isEmpty()
    }

    @Test
    fun schemelessUrlDigits_areNotNumbers() {
        assertThat(numbers("zoom.us/j/85012345678")).isEmpty()
    }

    @Test
    fun webLinkDigits_areNotNumbers() {
        assertThat(numbers("https://zoom.us/j/85012345678?pwd=1")).isEmpty()
    }
}
