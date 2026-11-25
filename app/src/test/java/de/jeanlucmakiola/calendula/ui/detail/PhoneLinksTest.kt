package de.jeanlucmakiola.calendula.ui.detail

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The fitting applied on top of the platform matcher. What the matcher itself
 * finds is covered on a device by PhoneLinksPlatformTest.
 */
class PhoneLinksTest {

    /** Refine a match the platform found at [found] in [text]. */
    private fun refine(text: String, found: String): List<PhoneLink> {
        val start = text.indexOf(found)
        return refinePhoneMatches(
            text,
            listOf(PhoneMatch(start, start + found.length)),
            findWebLinks(text),
        )
    }

    @Test
    fun `a match keeps its span and dials only digits`() {
        val text = "Call Anna on 0151 234-5678 before"
        val link = refine(text, "0151 234-5678").single()
        assertThat(link.number).isEqualTo("01512345678")
        assertThat(text.substring(link.start, link.end)).isEqualTo("0151 234-5678")
    }

    @Test
    fun `an international number keeps its plus`() {
        assertThat(refine("Tel. +49 30 1234567", "+49 30 1234567").single().number)
            .isEqualTo("+49301234567")
    }

    @Test
    fun `the trunk zero of an international number is dropped`() {
        assertThat(refine("+49 (0) 151 2345678", "+49 (0) 151 2345678").single().number)
            .isEqualTo("+491512345678")
    }

    @Test
    fun `a country code stranded before the trunk zero is pulled back in`() {
        val text = "Mobil: +49 (0)151 2345678"
        val link = refine(text, "151 2345678").single()
        assertThat(link.number).isEqualTo("+491512345678")
        assertThat(text.substring(link.start, link.end)).isEqualTo("+49 (0)151 2345678")
    }

    @Test
    fun `a conference dial-in keeps its pauses and meeting id`() {
        val text = "One tap mobile: +16465588656,,85012345678#,,,,*123456# (US)"
        val link = refine(text, "+16465588656").single()
        assertThat(link.number).isEqualTo("+16465588656,,85012345678#,,,,*123456#")
        assertThat(text.substring(link.start, link.end))
            .isEqualTo("+16465588656,,85012345678#,,,,*123456#")
    }

    @Test
    fun `meeting ids and PINs are not numbers to call`() {
        assertThat(refine("Meeting ID: 850 1234 5678", "850 1234 5678")).isEmpty()
        assertThat(refine("Conference ID: 123 456 789#", "123 456 789")).isEmpty()
        assertThat(refine("PIN: 123 456 789#", "123 456 789")).isEmpty()
        assertThat(refine("Konferenz-ID: 123 456 789#", "123 456 789")).isEmpty()
        assertThat(refine("Access code: 2345 678 9012", "2345 678 9012")).isEmpty()
    }

    @Test
    fun `a label on an earlier line does not hide a number`() {
        assertThat(refine("Meeting ID: 850 1234 5678\nDial +1 646 558 8656", "+1 646 558 8656"))
            .hasSize(1)
    }

    @Test
    fun `a label earlier on the same line does not hide a later number`() {
        assertThat(refine("PIN 1234, call +1 646 558 8656", "+1 646 558 8656")).hasSize(1)
    }

    @Test
    fun `a match holding a date is not a number`() {
        assertThat(refine("Am 14.10.2026 10:00 Uhr", "14.10.2026 10")).isEmpty()
        assertThat(refine("2026-10-14 10:00", "2026-10-14 10")).isEmpty()
        assertThat(refine("10/14/2026 9:00", "10/14/2026 9")).isEmpty()
        assertThat(refine("14.10.26 - 16.10.26", "14.10.26 - 16.10.26")).isEmpty()
    }

    @Test
    fun `amounts are not numbers`() {
        assertThat(refine("Budget 1.250.000 EUR", "1.250.000")).isEmpty()
        assertThat(refine("Budget 1 250 000 EUR", "1 250 000")).isEmpty()
    }

    @Test
    fun `an area code in threes is still a number`() {
        assertThat(refine("030 123 456", "030 123 456")).hasSize(1)
    }

    @Test
    fun `numbers in dotted pairs are not dates`() {
        assertThat(refine("01.23.45.67.89", "01.23.45.67.89").single().number)
            .isEqualTo("0123456789")
        assertThat(refine("555.123.4567", "555.123.4567")).hasSize(1)
    }

    @Test
    fun `a stray count after the number is trimmed`() {
        val text = "Call +49 151 2345678 2 times"
        val link = refine(text, "+49 151 2345678 2").single()
        assertThat(link.number).isEqualTo("+491512345678")
        assertThat(text.substring(link.start, link.end)).isEqualTo("+49 151 2345678")
    }

    @Test
    fun `short trailing groups of a real number stay`() {
        assertThat(refine("030 123 45 67", "030 123 45 67").single().number).isEqualTo("0301234567")
    }

    @Test
    fun `spaced threes without a currency are a number`() {
        assertThat(refine("Móvil 612 345 678", "612 345 678").single().number).isEqualTo("612345678")
    }

    @Test
    fun `a trailing pair after a short group stays`() {
        val text = "Büro 0221 1234 56"
        val link = refine(text, "0221 1234 56").single()
        assertThat(link.number).isEqualTo("0221123456")
        assertThat(text.substring(link.start, link.end)).isEqualTo("0221 1234 56")
    }

    @Test
    fun `an extension becomes a pause`() {
        assertThat(refine("+1 212 555 1234 ext. 567", "+1 212 555 1234 ext. 567").single().number)
            .isEqualTo("+12125551234,567")
        assertThat(refine("+1 212 555 1234 x567", "+1 212 555 1234 x567").single().number)
            .isEqualTo("+12125551234,567")
    }

    @Test
    fun `postcodes and other short runs are not numbers`() {
        assertThat(refine("Hauptstraße 12, 10115 Berlin", "10115")).isEmpty()
        assertThat(refine("Raum 123456", "123456")).isEmpty()
    }

    @Test
    fun `a Webex access code is not a number to call`() {
        assertThat(refine("Meeting number (access code): 2732 123 4567", "2732 123 4567")).isEmpty()
    }

    @Test
    fun `a conference number is a number to call`() {
        assertThat(refine("Conference number: +49 30 1234567", "+49 30 1234567")).hasSize(1)
        assertThat(refine("Konferenznummer: +49 30 1234567", "+49 30 1234567")).hasSize(1)
    }

    @Test
    fun `a comma-separated list stays separate numbers`() {
        val text = "Tel 030 1234567,0151 2345678"
        val first = text.indexOf("030")
        val second = text.indexOf("0151")
        val links = refinePhoneMatches(
            text,
            listOf(PhoneMatch(first, first + 11), PhoneMatch(second, text.length)),
            emptyList(),
        )
        assertThat(links.map { it.number }).containsExactly("0301234567", "01512345678").inOrder()
    }

    @Test
    fun `a single-comma suffix closed by a hash is a dial-in`() {
        assertThat(refine("+1 555 123 4567,123456#", "+1 555 123 4567").single().number)
            .isEqualTo("+15551234567,123456#")
    }

    @Test
    fun `labels behind invisible spacing still hide an id`() {
        assertThat(refine("PIN: \u202A123 456 789\u202C#", "123 456 789")).isEmpty()
        assertThat(refine("Meeting ID:\u00A0850 1234 5678", "850 1234 5678")).isEmpty()
    }

    @Test
    fun `dotted times of day are not numbers`() {
        assertThat(refine("Beginn 9.30 – 12.00 Uhr", "9.30 – 12.00")).isEmpty()
        assertThat(refine("10.00-11.30", "10.00-11.30")).isEmpty()
    }

    @Test
    fun `a trailing digit with no word after it stays`() {
        assertThat(refine("Zentrale 030 123456 0", "030 123456 0").single().number)
            .isEqualTo("0301234560")
        assertThat(refine("0221 9876543 21\nnext line", "0221 9876543 21").single().number)
            .isEqualTo("0221987654321")
    }

    @Test
    fun `digits in a URL without its scheme are not a number`() {
        assertThat(refine("zoom.us/j/85012345678", "85012345678")).isEmpty()
        assertThat(refine("room?id=0301234567", "0301234567")).isEmpty()
    }

    @Test
    fun `dotted threes without a currency are a number`() {
        assertThat(refine("Móvil 612.345.678", "612.345.678")).hasSize(1)
        assertThat(refine("Budget 125.000.000 €", "125.000.000")).isEmpty()
    }

    @Test
    fun `digits inside a web link are not a number`() {
        assertThat(refine("https://x.example/030-1234567", "030-1234567")).isEmpty()
    }

    @Test
    fun `a number beside a web link is still found`() {
        assertThat(refine("https://zoom.us/j/85012345678 or dial +1 646 558 8656", "+1 646 558 8656"))
            .hasSize(1)
    }
}
