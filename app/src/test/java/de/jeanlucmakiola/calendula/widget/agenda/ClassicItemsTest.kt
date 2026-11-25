package de.jeanlucmakiola.calendula.widget.agenda

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

/** The classic agenda's day labels and row cap (#489). */
class ClassicItemsTest {

    private val mon = LocalDate(2026, 11, 9)
    private val tue = LocalDate(2026, 11, 10)

    @Test
    fun `each day gets one label before its rows`() {
        val rows = listOf(AgendaRow.Placeholder(mon), AgendaRow.Placeholder(tue), AgendaRow.Placeholder(tue))
        assertThat(classicItems(rows).map { it::class }).containsExactly(
            ClassicItem.Header::class, ClassicItem.Entry::class,
            ClassicItem.Header::class, ClassicItem.Entry::class, ClassicItem.Entry::class,
        ).inOrder()
    }

    @Test
    fun `labels count towards the cap and a stranded one is dropped`() {
        val rows = listOf(AgendaRow.Placeholder(mon), AgendaRow.Placeholder(tue))
        val items = classicItems(rows, limit = 3)
        assertThat(items).containsExactly(ClassicItem.Header(mon), ClassicItem.Entry(rows[0])).inOrder()
    }
}
