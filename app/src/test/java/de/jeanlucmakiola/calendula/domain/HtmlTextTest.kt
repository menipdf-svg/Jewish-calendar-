package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HtmlTextTest {

    @Test
    fun `prose with angle brackets is not html`() {
        assertThat(looksLikeHtml("I <3 this, and a < b > c")).isFalse()
        assertThat(looksLikeHtml("Teams Meeting <https://teams.microsoft.com/l/x>")).isFalse()
        assertThat(looksLikeHtml("Bring <cake>")).isFalse()
    }

    @Test
    fun `common tags are html`() {
        assertThat(looksLikeHtml("Line one<br>Line two")).isTrue()
        assertThat(looksLikeHtml("<p class=MsoNormal>Hi</p>")).isTrue()
        assertThat(looksLikeHtml("See <a href=\"https://x.y\">here</a>")).isTrue()
    }

    @Test
    fun `angle-bracketed links and addresses stay text`() {
        val html = "Join<br>here <https://teams.microsoft.com/l/x> or <a@b.c>"
        assertThat(parseHtml(html).text).isEqualTo("Join\nhere <https://teams.microsoft.com/l/x> or <a@b.c>")
    }

    @Test
    fun `plain text passes through untouched`() {
        val text = "  Agenda:\n\n\n- one  \n- two"
        assertThat(descriptionRichText(text)).isEqualTo(RichText(text))
    }

    @Test
    fun `blocks and breaks become lines and whitespace collapses`() {
        val html = "<div>Hello\n   world</div><div>Second<br>Third</div>"
        assertThat(parseHtml(html).text).isEqualTo("Hello world\nSecond\nThird")
    }

    @Test
    fun `head, styles, scripts and comments are dropped`() {
        val html = """
            <html><head><meta charset="utf-8"><style>p { color: red; }</style></head>
            <body><!-- converted from text --><script>alert(1)</script><p>Body</p></body></html>
        """.trimIndent()
        assertThat(parseHtml(html).text).isEqualTo("Body")
    }

    @Test
    fun `an nbsp paragraph keeps one blank line, more collapse`() {
        val html = "<p>One</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>Two</p>"
        assertThat(parseHtml(html).text).isEqualTo("One\n\nTwo")
    }

    @Test
    fun `entities are decoded`() {
        assertThat(parseHtml("<p>Fish &amp; chips &lt;3 &#8364;5 &#x2013; caf&eacute; &bogus;</p>").text)
            .isEqualTo("Fish & chips <3 €5 – café &bogus;")
    }

    @Test
    fun `inline styles cover their text`() {
        val rich = parseHtml("<p>A <b>bold</b> and <i>italic</i> word</p>")
        assertThat(rich.text).isEqualTo("A bold and italic word")
        assertThat(rich.styles).containsExactly(
            StyleRange(2, 6, InlineStyle.Bold),
            StyleRange(11, 17, InlineStyle.Italic),
        )
    }

    @Test
    fun `fonts and colours are not kept`() {
        val rich = parseHtml("<span style=\"color:#000\"><font face=Calibri size=2>Text</font></span>")
        assertThat(rich).isEqualTo(RichText("Text"))
    }

    @Test
    fun `links keep their href, unsafe schemes are dropped`() {
        val rich = parseHtml(
            "<a href=\"https://teams.microsoft.com/l/x?a=1&amp;b=2\">Join</a> " +
                "<a href='tel:+4930123,,456#'>Dial</a> <a href=\"javascript:alert(1)\">Bad</a>",
        )
        assertThat(rich.text).isEqualTo("Join Dial Bad")
        assertThat(rich.links).containsExactly(
            LinkRange(0, 4, "https://teams.microsoft.com/l/x?a=1&b=2"),
            LinkRange(5, 9, "tel:+4930123,,456#"),
        )
    }

    @Test
    fun `lists get bullets and numbers`() {
        val html = "<p>Agenda</p><ul><li>One</li><li>Two<ol><li>Sub</li></ol></li></ul><ol><li>A</li><li>B</li></ol>"
        assertThat(parseHtml(html).text).isEqualTo("Agenda\n• One\n• Two\n  1. Sub\n1. A\n2. B")
    }

    @Test
    fun `table rows become lines with cells side by side`() {
        val html = "<table><tr><th>Time</th><th>Topic</th></tr><tr><td>9:00</td><td>Intro</td></tr>" +
            "<tr><td>&nbsp;</td><td>Spacer row</td></tr></table>"
        assertThat(parseHtml(html).text).isEqualTo("Time · Topic\n9:00 · Intro\nSpacer row")
    }

    @Test
    fun `formatting on a dropped spacer cell doesn't leak onto the next`() {
        val rich = parseHtml("<table><tr><td><b>&nbsp;</b></td><td>Plain</td></tr></table>")
        assertThat(rich).isEqualTo(RichText("Plain"))
    }

    @Test
    fun `pre keeps its whitespace`() {
        assertThat(parseHtml("<pre>a  b\n  c</pre>").text).isEqualTo("a  b\n  c")
    }

    @Test
    fun `spans shift with trimmed whitespace`() {
        val rich = parseHtml("<p>&nbsp;</p><p>  <b>Bold</b>   </p>")
        assertThat(rich.text).isEqualTo("Bold")
        assertThat(rich.styles).containsExactly(StyleRange(0, 4, InlineStyle.Bold))
    }

    @Test
    fun `broken markup stays text`() {
        assertThat(parseHtml("<p>x < y and <b>z</p>").text).isEqualTo("x < y and z")
        assertThat(parseHtml("<p>open <a href=\"https://a.b\">tail").links)
            .containsExactly(LinkRange(5, 9, "https://a.b"))
    }

    @Test
    fun `a paragraph inside a list item stays on the bullet's line`() {
        assertThat(parseHtml("<ul><li><p>One</p></li><li><div>Two</div></li></ul>").text)
            .isEqualTo("• One\n• Two")
    }

    @Test
    fun `newlines are the lines when the markup sets none`() {
        val rich = parseHtml("Agenda:\n1. Intro\n2. <a href=\"https://x.y\">Q&amp;A</a>")
        assertThat(rich.text).isEqualTo("Agenda:\n1. Intro\n2. Q&A")
    }

    @Test
    fun `newlines in markup with its own lines are just spaces`() {
        assertThat(parseHtml("<p>wrapped\nsource line</p>").text).isEqualTo("wrapped source line")
    }

    @Test
    fun `a link scheme is lower-cased`() {
        assertThat(parseHtml("<a href=\"HTTPS://Teams.example/Join\">Join</a>").links.single().url)
            .isEqualTo("https://Teams.example/Join")
    }

    @Test
    fun `a query parameter is not read as an entity`() {
        assertThat(parseHtml("<a href=\"https://x.example/?id=5&reg=eu&times=3\">x</a>").links.single().url)
            .isEqualTo("https://x.example/?id=5&reg=eu&times=3")
    }

    @Test
    fun `outlook cells with paragraphs still sit side by side`() {
        val html = "<table><tr><td><p class=MsoNormal>9:00</p></td><td><p class=MsoNormal><b>Intro</b></p></td></tr>" +
            "<tr><td><p>10:00</p></td><td><p>Talk</p></td></tr></table>"
        val rich = parseHtml(html)
        assertThat(rich.text).isEqualTo("9:00 · Intro\n10:00 · Talk")
        assertThat(rich.styles).containsExactly(StyleRange(7, 12, InlineStyle.Bold))
    }

    @Test
    fun `empty cells anywhere in a row leave no separator`() {
        val html = "<table><tr><td>Time</td><td>&nbsp;</td><td>Topic</td><td></td></tr></table>"
        assertThat(parseHtml(html).text).isEqualTo("Time · Topic")
    }

    @Test
    fun `a trailing empty cell doesn't join the text after the table`() {
        val html = "<table><tr><td>10:00</td><td>Demo</td><td></td></tr></table><p>After</p>"
        assertThat(parseHtml(html).text).isEqualTo("10:00 · Demo\nAfter")
    }

    @Test
    fun `an unclosed head or style doesn't swallow the description`() {
        assertThat(parseHtml("<html><head><meta charset=utf-8><body><p>Agenda</p>").text).isEqualTo("Agenda")
        assertThat(parseHtml("<style>p{}<p>Agenda</p>").text).contains("Agenda")
    }

    @Test
    fun `a list in a later cell keeps its bullet clean`() {
        val html = "<table><tr><td>Agenda</td><td><ul><li>Intro</li></ul></td></tr></table>"
        assertThat(parseHtml(html).text).isEqualTo("Agenda\n• Intro")
    }

    @Test
    fun `an empty trailing item doesn't pull the next paragraph into the list`() {
        assertThat(parseHtml("<ul><li>A</li><li></li></ul><p>Next steps</p>").text).isEqualTo("• A\nNext steps")
    }

    @Test
    fun `an ordered list honours its start`() {
        assertThat(parseHtml("<ol start=\"3\"><li>C</li><li>D</li></ol>").text).isEqualTo("3. C\n4. D")
    }

    @Test
    fun `every tag the renderer knows counts as html`() {
        assertThat(looksLikeHtml("Run <code>make deploy</code>")).isTrue()
        assertThat(looksLikeHtml("Price <del>50</del> 40")).isTrue()
    }

    @Test
    fun `latin-1 entities decode`() {
        assertThat(decodeEntities("Le&oacute;n, &iacute;ndice, &Aring;s, &yuml;")).isEqualTo("León, índice, Ås, ÿ")
    }

    @Test
    fun `copied text keeps the addresses of its links`() {
        val rich = parseHtml(
            "<p><a href=\"https://teams.example/join\">Join</a> or <a href=\"https://x.y\">https://x.y</a>" +
                " <a href=\"mailto:a@b.c\">Mail</a></p>",
        )
        assertThat(rich.copyText()).isEqualTo("Join (https://teams.example/join) or https://x.y Mail (a@b.c)")
    }
}
