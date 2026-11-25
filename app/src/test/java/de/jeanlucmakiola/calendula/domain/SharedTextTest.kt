package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SharedTextTest {

    @Test
    fun `subject becomes the title and the text the description`() {
        assertThat(sharedTextOrNull("Dinner", "Friday at Luigi's\nBring wine"))
            .isEqualTo(SharedText("Dinner", "Friday at Luigi's\nBring wine"))
    }

    @Test
    fun `without a subject the first line is the title and the rest the description`() {
        assertThat(sharedTextOrNull(null, "\n  Dentist  \n\nCheck-up, room 4\n"))
            .isEqualTo(SharedText("Dentist", "Check-up, room 4"))
    }

    @Test
    fun `a single line is the title only`() {
        assertThat(sharedTextOrNull("  ", " Call mum ")).isEqualTo(SharedText("Call mum", ""))
    }

    @Test
    fun `subject without text leaves the description empty`() {
        assertThat(sharedTextOrNull("Meeting", null)).isEqualTo(SharedText("Meeting", ""))
    }

    @Test
    fun `a long first line is cut and the full text kept as the description`() {
        val line = "a".repeat(300)
        val shared = sharedTextOrNull(null, "$line\nmore")!!
        assertThat(shared.title).isEqualTo("a".repeat(255))
        assertThat(shared.description).isEqualTo("$line\nmore")
    }

    @Test
    fun `a long subject is cut and kept in full above the text`() {
        val subject = "s".repeat(300)
        val shared = sharedTextOrNull(subject, "body")!!
        assertThat(shared.title).hasLength(255)
        assertThat(shared.description).isEqualTo("$subject\n\nbody")
    }

    @Test
    fun `the cut never splits a surrogate pair`() {
        val shared = sharedTextOrNull(null, "a".repeat(254) + "😀" + "b")!!
        assertThat(shared.title).isEqualTo("a".repeat(254))
    }

    @Test
    fun `nothing usable is null`() {
        assertThat(sharedTextOrNull(null, null)).isNull()
        assertThat(sharedTextOrNull(" ", "\n \n")).isNull()
    }
}
