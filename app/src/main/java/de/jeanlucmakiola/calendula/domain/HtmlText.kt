package de.jeanlucmakiola.calendula.domain

/** Inline formatting an HTML description keeps; fonts, sizes and colours are dropped. */
enum class InlineStyle { Bold, Italic, Underline, Strikethrough, Superscript, Subscript, Monospace }

data class StyleRange(val start: Int, val end: Int, val style: InlineStyle)

/** A link from an `<a href>`, spanning [start] until [end] (exclusive). */
data class LinkRange(val start: Int, val end: Int, val url: String)

/** Text as drawn, with the formatting and links that sit on it. Plain text has neither. */
data class RichText(
    val text: String,
    val styles: List<StyleRange> = emptyList(),
    val links: List<LinkRange> = emptyList(),
) {
    /**
     * [text] with each link's address after it, where the text doesn't already
     * show it, so a copied "Join the meeting" still carries its URL.
     */
    fun copyText(): String {
        if (links.isEmpty()) return text
        val out = StringBuilder(text)
        for (link in links.sortedByDescending { it.end }) {
            val address = link.url.removePrefix("mailto:").removePrefix("tel:")
            if (address !in text.substring(link.start, link.end)) out.insert(link.end, " ($address)")
        }
        return out.toString()
    }
}

fun looksLikeHtml(text: String): Boolean = htmlTagRegex.containsMatchIn(text)

/** [description] as it reads: HTML rendered down to [RichText], plain text as it is. */
fun descriptionRichText(description: String): RichText =
    if (looksLikeHtml(description)) parseHtml(description) else RichText(description)

private val BLOCK_TAGS = setOf(
    "p", "div", "ul", "ol", "li", "table", "tr", "blockquote", "pre", "hr", "section", "article",
    "header", "footer", "dl", "dt", "dd", "address", "center", "h1", "h2", "h3", "h4", "h5", "h6",
)

// Their content is never text to show.
private val SKIPPED_TAGS = setOf("head", "style", "script", "title", "xml", "template")

private val STYLE_TAGS = mapOf(
    "b" to InlineStyle.Bold, "strong" to InlineStyle.Bold, "th" to InlineStyle.Bold,
    "h1" to InlineStyle.Bold, "h2" to InlineStyle.Bold, "h3" to InlineStyle.Bold,
    "h4" to InlineStyle.Bold, "h5" to InlineStyle.Bold, "h6" to InlineStyle.Bold,
    "i" to InlineStyle.Italic, "em" to InlineStyle.Italic, "cite" to InlineStyle.Italic,
    "u" to InlineStyle.Underline, "ins" to InlineStyle.Underline,
    "s" to InlineStyle.Strikethrough, "strike" to InlineStyle.Strikethrough, "del" to InlineStyle.Strikethrough,
    "sup" to InlineStyle.Superscript, "sub" to InlineStyle.Subscript,
    "code" to InlineStyle.Monospace, "tt" to InlineStyle.Monospace, "pre" to InlineStyle.Monospace,
)

// A real tag the renderer knows, not prose like "<3" or "a < b". Google
// Calendar and Exchange both store their descriptions as HTML.
private val htmlTagRegex = Regex(
    (BLOCK_TAGS + SKIPPED_TAGS + STYLE_TAGS.keys + setOf("html", "body", "br", "span", "a", "font", "img", "meta", "td"))
        .joinToString("|", prefix = """</?(?:""", postfix = """)(?:\s[^<>]*)?/?>"""),
    RegexOption.IGNORE_CASE,
)

private val LINK_SCHEMES = listOf("http://", "https://", "mailto:", "tel:")

private const val NBSP = '\u00A0'
private const val CELL_SEPARATOR = " · "

/**
 * Render [html] down to text with its inline formatting and links. Blocks and
 * `<br>` become line breaks, list items get a bullet or number, table rows a
 * line each with their cells side by side. Lenient about broken markup:
 * whatever doesn't parse as a tag stays text.
 */
fun parseHtml(html: String): RichText = HtmlRenderer(html).render()

// Markup that sets its own lines. Without any, the text's newlines are the
// lines: "Agenda:\n1. Intro" with one <a href> in it stays on two lines.
private val lineTagRegex = Regex("""<(?:br|p|div|li|tr|h[1-6]|pre|blockquote|table)\b""", RegexOption.IGNORE_CASE)

private class HtmlRenderer(private val html: String) {
    private val out = StringBuilder()
    private val styles = mutableListOf<StyleRange>()
    private val links = mutableListOf<LinkRange>()
    private val openStyles = mutableListOf<Pair<String, Int>>()
    private var openLink: Pair<String, Int>? = null
    private val lists = mutableListOf<ListState>()
    private val keepNewlines = !lineTagRegex.containsMatchIn(html)
    private var preDepth = 0
    private var rowStart = 0
    private var cellNeedsSeparator = false
    // Where the current list item's bullet ends, so a <p> inside the item
    // doesn't push its text off the bullet's line.
    private var markerEnd = -1

    private class ListState(val ordered: Boolean, var count: Int = 0)

    fun render(): RichText {
        var i = 0
        while (i < html.length) {
            val lt = html.indexOf('<', i)
            if (lt < 0) {
                text(html.substring(i))
                break
            }
            if (lt > i) text(html.substring(i, lt))
            i = tag(lt)
        }
        return normalize()
    }

    /** Handle the markup at [lt]; returns where reading continues. */
    private fun tag(lt: Int): Int {
        if (html.startsWith("<!--", lt)) {
            val end = html.indexOf("-->", lt + 4)
            return if (end < 0) html.length else end + 3
        }
        if (html.startsWith("<!", lt) || html.startsWith("<?", lt)) {
            val end = html.indexOf('>', lt)
            return if (end < 0) html.length else end + 1
        }
        val closing = html.getOrNull(lt + 1) == '/'
        val nameStart = if (closing) lt + 2 else lt + 1
        if (html.getOrNull(nameStart)?.isLetter() != true) {
            text("<")
            return lt + 1
        }
        val end = tagEnd(lt)
        if (end < 0) {
            text("<")
            return lt + 1
        }
        if (!closing && isAutolink(html.substring(nameStart, end))) {
            text(html.substring(lt, end + 1))
            return end + 1
        }
        var nameEnd = nameStart
        while (nameEnd < end && (html[nameEnd].isLetterOrDigit() || html[nameEnd] == ':')) nameEnd++
        val name = html.substring(nameStart, nameEnd).lowercase()
        val attrs = html.substring(nameEnd, end)
        if (closing) {
            close(name)
        } else if (name in SKIPPED_TAGS && !attrs.trimEnd().endsWith("/")) {
            // Never closed (an omitted </head> is valid): just ignore the tag.
            val closeAt = html.indexOf("</$name", end, ignoreCase = true)
            if (closeAt < 0) return end + 1
            val closeEnd = html.indexOf('>', closeAt)
            return if (closeEnd < 0) html.length else closeEnd + 1
        } else {
            open(name, attrs)
        }
        return end + 1
    }

    /** An angle-bracketed URL or address (`<https://…>`, `<a@b.c>`), kept as text. */
    private fun isAutolink(inner: String): Boolean =
        LINK_SCHEMES.any { inner.startsWith(it, ignoreCase = true) } ||
            ('@' in inner && inner.none { it.isWhitespace() || it == '=' })

    /** The `>` closing the tag at [lt], skipping any inside quoted attributes. */
    private fun tagEnd(lt: Int): Int {
        var quote: Char? = null
        for (j in lt + 1 until html.length) {
            val c = html[j]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return j
                c == '<' -> return -1
            }
        }
        return -1
    }

    private fun open(name: String, attrs: String) {
        when (name) {
            "br" -> newline()
            "li" -> {
                markerEnd = -1
                cellNeedsSeparator = false
                lineBreak()
                val list = lists.lastOrNull()
                out.append("$NBSP$NBSP".repeat((lists.size - 1).coerceAtLeast(0)))
                if (list?.ordered == true) {
                    list.count++
                    out.append("${list.count}. ")
                } else {
                    out.append("• ")
                }
                markerEnd = out.length
            }
            "ul", "ol" -> {
                cellNeedsSeparator = false
                lineBreak()
                val start = startRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                lists += ListState(ordered = name == "ol", count = start - 1)
            }
            "tr" -> {
                lineBreak()
                rowStart = out.length
                cellNeedsSeparator = false
            }
            "td", "th" -> {
                // An empty spacer cell leaves nothing behind, not even its &nbsp;.
                if (rowStart <= out.length && (rowStart until out.length).none { out[it].isContent() }) {
                    cut(rowStart)
                } else {
                    cellNeedsSeparator = true
                }
            }
            "a" -> {
                closeLink()
                hrefRegex.find(attrs)
                    ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                    ?.let(::decodeEntities)
                    ?.trim()
                    ?.let { href ->
                        val scheme = LINK_SCHEMES.firstOrNull { href.startsWith(it, ignoreCase = true) }
                            ?: return@let
                        // Intent filters match the scheme case-sensitively.
                        openLink = scheme + href.substring(scheme.length) to out.length
                    }
            }
            else -> if (name in BLOCK_TAGS) lineBreak()
        }
        if (name == "pre") preDepth++
        STYLE_TAGS[name]?.let { openStyles += name to out.length }
    }

    private fun close(name: String) {
        // A row's last cell, if empty, leaves a separator pending; it ends with the row.
        if (name == "tr" || name == "table") cellNeedsSeparator = false
        val index = openStyles.indexOfLast { it.first == name }
        if (index >= 0) {
            val (_, start) = openStyles.removeAt(index)
            if (out.length > start) styles += StyleRange(start, out.length, STYLE_TAGS.getValue(name))
        }
        if (name == "pre") preDepth = (preDepth - 1).coerceAtLeast(0)
        if (name == "li" || name == "ul" || name == "ol") {
            if (out.length == markerEnd) cut(out.lastIndexOf("\n") + 1)
            markerEnd = -1
        }
        when (name) {
            "ul", "ol" -> {
                lists.removeLastOrNull()
                lineBreak()
            }
            "a" -> closeLink()
            else -> if (name in BLOCK_TAGS) lineBreak()
        }
    }

    private fun closeLink() {
        val (url, start) = openLink ?: return
        if (out.length > start) links += LinkRange(start, out.length, url)
        openLink = null
    }

    private fun text(raw: String) {
        val decoded = decodeEntities(raw)
        if (preDepth > 0) {
            out.append(decoded.replace("\r\n", "\n"))
            return
        }
        for (c in decoded) {
            if (c == '\n' && keepNewlines) {
                newline()
            } else if (c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\u000C') {
                if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ')
            } else {
                if (cellNeedsSeparator && c.isContent()) separateCell()
                out.append(c)
            }
        }
    }

    /** Put this cell's text beside the row's last, even after a block in that cell ended its line. */
    private fun separateCell() {
        cellNeedsSeparator = false
        var end = out.length
        while (end > rowStart && !out[end - 1].isContent()) end--
        cut(end)
        out.append(CELL_SEPARATOR)
        openStyles.replaceAll { (name, start) -> name to if (start >= end) out.length else start }
        openLink = openLink?.let { (url, start) -> url to if (start >= end) out.length else start }
    }

    private fun Char.isContent() = !isWhitespace() && this != NBSP

    private fun newline() {
        trimTrailingSpaces()
        out.append('\n')
    }

    /** End the current line, unless it's already ended or holds just a bullet. */
    private fun lineBreak() {
        if (out.length == markerEnd) return
        trimTrailingSpaces()
        if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
    }

    private fun trimTrailingSpaces() {
        while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
    }

    /** Drop the output from [length] on, with whatever formatting lay only there. */
    private fun cut(length: Int) {
        if (length >= out.length) return
        out.setLength(length)
        styles.replaceAll { it.copy(end = minOf(it.end, length)) }
        styles.removeAll { it.end <= it.start }
        links.replaceAll { it.copy(end = minOf(it.end, length)) }
        links.removeAll { it.end <= it.start }
        openStyles.replaceAll { (name, start) -> name to minOf(start, length) }
        openLink = openLink?.let { (url, start) -> url to minOf(start, length) }
    }

    /**
     * Tidy the rendered text: no-break spaces become plain, lines lose trailing
     * blanks, runs of empty lines shrink to one and the ends are trimmed. Spans
     * move with the text they cover.
     */
    private fun normalize(): RichText {
        closeLink()
        for ((name, start) in openStyles) {
            if (out.length > start) styles += StyleRange(start, out.length, STYLE_TAGS.getValue(name))
        }
        val src = out.toString().replace(NBSP, ' ')
        val map = IntArray(src.length + 1)
        val result = StringBuilder()
        var i = 0
        while (i < src.length) {
            var lineEnd = src.indexOf('\n', i)
            if (lineEnd < 0) lineEnd = src.length
            var contentEnd = lineEnd
            while (contentEnd > i && src[contentEnd - 1].isWhitespace()) contentEnd--
            for (j in i until lineEnd) {
                map[j] = result.length
                if (j < contentEnd) result.append(src[j])
            }
            if (lineEnd < src.length) {
                map[lineEnd] = result.length
                if (!result.endsWith("\n\n")) result.append('\n')
            }
            i = lineEnd + 1
        }
        map[src.length] = result.length
        val leading = result.length - result.trimStart().length
        val text = result.trim().toString()
        fun at(index: Int) = (map[index] - leading).coerceIn(0, text.length)
        return RichText(
            text = text,
            styles = styles.mapNotNull { r -> StyleRange(at(r.start), at(r.end), r.style).takeIf { it.end > it.start } },
            links = links.mapNotNull { r -> LinkRange(at(r.start), at(r.end), r.url).takeIf { it.end > it.start } },
        )
    }
}

private val startRegex = Regex("""(?:^|\s)start\s*=\s*["']?(-?\d{1,6})""", RegexOption.IGNORE_CASE)
private val hrefRegex = Regex("""(?:^|\s)href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

// Latin-1 (&nbsp; … &yuml;) in code point order, then the common extras.
private val LATIN1_ENTITIES = ("nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr " +
        "deg plusmn sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest " +
        "Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml " +
        "ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig " +
        "agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml " +
        "eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml").split(' ')

private val ENTITIES: Map<String, String> = LATIN1_ENTITIES.withIndex()
    .associate { (i, name) -> name to (0xA0 + i).toChar().toString() } + mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "ndash" to "–", "mdash" to "—", "hellip" to "…", "bull" to "•",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "sbquo" to "‚", "bdquo" to "„",
    "euro" to "€", "trade" to "™", "OElig" to "Œ", "oelig" to "œ", "Scaron" to "Š", "scaron" to "š",
    "zwnj" to "\u200C", "zwj" to "\u200D", "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009",
)

// A named reference needs its ";": "?id=5&reg=eu" in a link is a query, not "®".
private val entityRegex = Regex("""&(?:(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6});?|([a-zA-Z][a-zA-Z0-9]{1,31});)""")

/** Decode character references; an unknown name is left as written. */
internal fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    return entityRegex.replace(text) { match ->
        val name = match.groupValues[2]
        if (name.isNotEmpty()) return@replace ENTITIES[name] ?: match.value
        val body = match.groupValues[1]
        val code = when {
            body.startsWith("#x", ignoreCase = true) -> body.substring(2).toIntOrNull(16)
            else -> body.substring(1).toIntOrNull()
        }
        if (code == null || code == 0 || !Character.isValidCodePoint(code)) "�" else String(Character.toChars(code))
    }
}
