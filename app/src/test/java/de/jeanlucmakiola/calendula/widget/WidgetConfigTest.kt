package de.jeanlucmakiola.calendula.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.floret.glance.WidgetBackground
import org.junit.jupiter.api.Test

/** Per-widget options survive a save and read back (#489). */
class WidgetConfigTest {

    private val defaults = AgendaWidgetOptions(AgendaRange.Week, WidgetSize.SMALL, true, PastEventDisplay.SHOW)

    @Test
    fun `appearance and agenda style round-trip`() {
        val appearance = WidgetAppearance(WidgetBackground.SOLID, WidgetTheme.DARK, opacity = 0.6f)
        val options = defaults.copy(style = AgendaWidgetStyle.CLASSIC)
        val prefs = mutablePreferencesOf().apply {
            putAppearance(appearance)
            putAgendaOptions(options)
        }
        assertThat(prefs.widgetAppearance).isEqualTo(appearance)
        assertThat(prefs.agendaWidgetOptions(defaults)).isEqualTo(options)
    }

    @Test
    fun `a widget saved before 2_24_1 stays opaque cards`() {
        assertThat(emptyPreferences().widgetAppearance.opacity).isEqualTo(1f)
        assertThat(emptyPreferences().agendaWidgetOptions(defaults).style).isEqualTo(AgendaWidgetStyle.CARDS)
    }
}
