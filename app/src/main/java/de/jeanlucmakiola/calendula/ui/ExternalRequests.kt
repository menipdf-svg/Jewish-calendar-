package de.jeanlucmakiola.calendula.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.ui.edit.ImportSource

/**
 * What a notification, widget, shortcut or another app asked the running app to
 * open. MainActivity fills it from each intent (singleTop + onNewIntent route a
 * new one into the running activity); CalendarHost acts on a request and clears
 * it, so a later recomposition can't act on it twice.
 *
 * [onExternalFormClosed] runs when a form opened for [insert] closes, so the
 * activity can hand control back to the app that asked.
 */
@Stable
class ExternalRequests(val onExternalFormClosed: () -> Unit = {}) {
    /** A tapped reminder notification's occurrence (eventId, begin, end), for the detail screen. */
    var detailKey by mutableStateOf<LongArray?>(null)

    /** A home-screen widget, shortcut or tile navigation (open a date, an event, a view, or create). */
    var nav by mutableStateOf<WidgetNavRequest?>(null)

    /** An .ics file opened or shared into the app (ACTION_VIEW/SEND). */
    var importUri by mutableStateOf<Uri?>(null)

    /**
     * A prefilled new-event form from an external ACTION_INSERT launch (#30) or
     * shared text (#306), opened in the create form for review.
     */
    var insert by mutableStateOf<InsertRequest?>(null)

    /** An external "edit this event" (ACTION_EDIT), in the detail key's shape. */
    var editKey by mutableStateOf<LongArray?>(null)

    /** Whether these came from re-reading the launch intent on a recreation. */
    var replayed = false
}

/** A prefilled create form from an external launch, with the source it came from. */
data class InsertRequest(val form: EventForm, val source: ImportSource)
