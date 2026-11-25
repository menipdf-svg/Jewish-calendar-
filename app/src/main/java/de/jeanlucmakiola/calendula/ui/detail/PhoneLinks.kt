package de.jeanlucmakiola.calendula.ui.detail

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify

/**
 * A phone number found in free text, spanning [start] until [end] (exclusive).
 * [number] is what the dialer gets: digits, a leading `+`, and any `,`/`#`
 * dial-in suffix — the separators that only made it readable are gone.
 */
data class PhoneLink(val start: Int, val end: Int, val number: String)

/** The phone numbers in an event's free-text fields, each against its own text. */
data class EventPhoneLinks(
    val title: List<PhoneLink> = emptyList(),
    val location: List<PhoneLink> = emptyList(),
    val description: List<PhoneLink> = emptyList(),
)

/** Where the platform matcher saw a phone number: [start] until [end] (exclusive). */
internal data class PhoneMatch(val start: Int, val end: Int)

// A conference dial-in's pauses and meeting id right after the number:
// "+16465588656,,85012345678#" or Zoom's ",,850…#,,,,*123456#". It opens on a
// double comma or closes on "#", so "030 1234567,0151 2345678" stays a list.
private val dialSuffixRegex = Regex("""^(?:,,+[\d*]+#?|,[\d*]+#)(?:,+[\d*]+#?)*""")

// Whitespace as invites write it: Java's \s misses the no-break space and the
// invisible direction marks Meet and Outlook wrap around numbers.
private const val GAP = """\s\u00A0\u200E\u200F\u202A-\u202E\u2066-\u2069"""

// A number right after one of these labels is an id to type in, not one to
// call. English and German cover the Zoom/Teams/Meet/Webex invites seen so far.
// "Conference number" is left out: invites use it for the dial-in itself.
private val notAPhoneLabelRegex = Regex(
    """(?:(?:meeting|webinar|besprechungs)[ -]?(?:id|number|nummer)|(?:conference|konferenz)[ -]?id|""" +
        """\bpin|passcode|password|passwort|kenncode|access code|zugangscode)[$GAP:#.()]*$""",
    RegexOption.IGNORE_CASE,
)

// A day.month.year (or year-month-day, or US month/day/year) date: exactly
// three groups, so a French "01.23.45.67.89" chain doesn't read as one.
private val dateRegex = Regex("""(?<![\d./-])(\d{1,4})([./-])(\d{1,2})\2(\d{4}|\d{2})(?![\d]|[./-]\d)""")

// Thousands groups. A short lead ("1.250.000", "12 500 000") is an amount; a
// three-digit lead is one only beside a currency — "612 345 678" and
// "612.345.678" are Spanish mobiles, "125.000.000 €" a budget.
private val shortLeadAmountRegex = Regex("""[1-9]\d?(?:([. ])\d{3})(?:\1\d{3})*""")
private val amountRegex = Regex("""[1-9]\d{0,2}(?:([. ])\d{3})(?:\1\d{3})*""")
private val currencyBeforeRegex = Regex("""(?:[€$£]|\b(?:eur|euro|usd|chf|gbp))\s*$""", RegexOption.IGNORE_CASE)
private val currencyAfterRegex = Regex("""^\s*(?:[€$£]|(?:eur|euro|usd|chf|gbp)\b)""", RegexOption.IGNORE_CASE)

// A short group set off by a space after a long one, with a word straight
// after — "… 2345678 2 times" — is the prose's count, not the number's tail.
// Without the word ("030 123456 0") or after a short group ("0221 1234 56")
// it is the number's own.
private val strayTailRegex = Regex("""(?<=\d{6})\s+\d{1,2}$""")
private val wordAfterRegex = Regex("""[ \t\u00A0]+\p{L}""")

// "9.30 – 12.00", "10.00-11.30": times of day written the German way.
private val dottedTimesRegex = Regex("""(\d{1,2})\.(\d{2})(?:\s*[-–]\s*(\d{1,2})\.(\d{2}))?""")

// "ext. 567", "x567", "Durchwahl 567": the dialer reaches an extension after a pause.
private val extensionRegex = Regex("""\s*(?:ext\.?|extension|x|durchwahl|dw\.?)\s*(?=\d)""", RegexOption.IGNORE_CASE)

private val trunkPrefixRegex = Regex("""\(\s*0\s*\)""")
private val strandedCountryCodeRegex = Regex("""\+\d{1,3}\s*\(\s*0\s*\)\s*$""")

// Short local numbers exist, but in event text a 5- or 6-digit run is far more
// often a postcode, house number or booking reference.
private const val MIN_DIGITS = 7

/**
 * Find the phone numbers in [text]. The platform's libphonenumber-backed
 * matcher does the finding — it knows the device's region and real number
 * lengths; [refinePhoneMatches] then fits what it found to an event's text.
 * Call off the main thread: the matcher loads its metadata on first use.
 * No match at all when the platform matcher fails.
 */
internal fun findPhoneLinks(text: String): List<PhoneLink> =
    refinePhoneMatches(text, platformPhoneMatches(text), findWebLinks(text))

/** [findPhoneLinks] for each of an event's free-text fields. */
internal fun findEventPhoneLinks(title: String, location: String?, description: String?) = EventPhoneLinks(
    title = findPhoneLinks(title),
    location = location?.let(::findPhoneLinks).orEmpty(),
    description = description?.let(::findPhoneLinks).orEmpty(),
)

// The matcher reads the region off the SIM, which throws on a device without
// telephony; a field then just goes without phone links.
private fun platformPhoneMatches(text: String): List<PhoneMatch> = runCatching {
    val spannable = SpannableString(text)
    if (!Linkify.addLinks(spannable, Linkify.PHONE_NUMBERS)) return emptyList()
    spannable.getSpans(0, spannable.length, URLSpan::class.java)
        .map { PhoneMatch(spannable.getSpanStart(it), spannable.getSpanEnd(it)) }
}.getOrDefault(emptyList())

/**
 * Turn the platform's [matches] in [text] into dialable links. The matcher is
 * lenient, so a match is dropped when it sits inside one of the [webLinks],
 * follows a meeting id or PIN label, holds a date, reads as an amount or is
 * too short. What's kept loses a stray count off its end, regains an
 * international "+49 (0)" prefix the matcher left behind, and carries a
 * conference dial-in suffix along.
 */
internal fun refinePhoneMatches(
    text: String,
    matches: List<PhoneMatch>,
    webLinks: List<WebLink>,
): List<PhoneLink> {
    val links = mutableListOf<PhoneLink>()
    for (match in matches.sortedBy { it.start }) {
        var start = match.start
        var end = match.end
        val matched = text.substring(start, end)
        if (webLinks.any { start < it.end && it.start < end }) continue
        if (gluedIntoAWord(text, start, end)) continue
        val lineBefore = text.window(lineStart(text, start).coerceAtLeast(start - LOOK_BEHIND), start)
        if (notAPhoneLabelRegex.containsMatchIn(lineBefore)) continue
        if (dateRegex.findAll(matched).any { it.isDate() }) continue
        if (isDottedTimes(matched)) continue
        if (isAmount(text, start, end)) continue
        strayTailRegex.find(matched)?.let { tail ->
            if (wordAfterRegex.matchesAt(text, end)) end = start + tail.range.first
        }
        // "+49 (0)151 …": a matcher that stops at the "(0)" leaves the domestic
        // tail, which would dial without the country code.
        strandedCountryCodeRegex.find(text.window(start - LOOK_BEHIND, start))
            ?.let { start -= it.value.length }
        val number = dialable(text.substring(start, end))
        if (number.count { it.isDigit() } < MIN_DIGITS) continue
        val suffix = dialSuffixRegex.find(text.window(end, end + LOOK_AHEAD))?.value.orEmpty()
        // A suffix swallowed what the matcher saw as the next number.
        if (links.lastOrNull()?.let { start < it.end } == true) continue
        links += PhoneLink(start, end + suffix.length, number + dialable(suffix))
    }
    return links
}

// How far around a match the label, prefix, currency and suffix checks look.
// Enough for any of them, and it keeps a long invite from being re-read whole
// for every number in it.
private const val LOOK_BEHIND = 40
private const val LOOK_AHEAD = 60

private fun String.window(from: Int, to: Int): String =
    substring(from.coerceAtLeast(0), to.coerceAtMost(length))

/**
 * Digits run straight into a letter or a URL path — "zoom.us/j/850…" without
 * its https:// — which no web link covered. A phone number stands apart.
 */
private fun gluedIntoAWord(text: String, start: Int, end: Int): Boolean {
    val before = text.getOrNull(start - 1)
    val after = text.getOrNull(end)
    return (before != null && (before.isLetter() || before in "/=?&")) ||
        (after != null && (after.isLetter() || after == '/'))
}

private fun isDottedTimes(matched: String): Boolean {
    val times = dottedTimesRegex.matchEntire(matched) ?: return false
    return times.groupValues.drop(1).chunked(2).all { (hour, minute) ->
        hour.isEmpty() || (hour.toInt() in 0..24 && minute.toInt() in 0..59)
    }
}

private fun isAmount(text: String, start: Int, end: Int): Boolean {
    val matched = text.substring(start, end)
    if (shortLeadAmountRegex.matches(matched)) return true
    return amountRegex.matches(matched) &&
        (currencyBeforeRegex.containsMatchIn(text.window(start - LOOK_BEHIND, start)) ||
            currencyAfterRegex.containsMatchIn(text.window(end, end + LOOK_AHEAD)))
}

private fun MatchResult.isDate(): Boolean {
    val (a, _, b, c) = destructured
    val first = a.toInt()
    val second = b.toInt()
    val last = c.toInt()
    return when {
        a.length == 4 -> second in 1..12 && last in 1..31 && c.length <= 2
        a.length > 2 -> false
        // Day first (most of the world) or month first (US).
        else -> (first in 1..31 && second in 1..12) || (first in 1..12 && second in 1..31)
    }
}

private fun lineStart(text: String, index: Int): Int = text.lastIndexOf('\n', index - 1) + 1

/**
 * Strip [raw] down to what a dialer takes. An international number's "(0)"
 * trunk prefix — "+49 (0) 151 …" — is a domestic-only hint and would break the
 * call, so it goes too; an extension becomes a pause.
 */
private fun dialable(raw: String): String {
    val international = raw.trimStart('(').startsWith("+")
    val withoutTrunk = if (international) raw.replace(trunkPrefixRegex, "") else raw
    return withoutTrunk.replace(extensionRegex, ",")
        .filter { it.isDigit() || it == '+' || it == ',' || it == '#' || it == '*' }
}
