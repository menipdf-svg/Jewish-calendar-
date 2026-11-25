package de.jeanlucmakiola.calendula.ui.detail

import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.domain.RichText

/**
 * UI state for the event-detail screen (spec S4).
 */
sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data class Failure(val reason: FailureReason) : EventDetailUiState
    data class Success(
        val detail: EventDetail,
        /** Display name of the owning calendar, null if it can't be resolved. */
        val calendarName: String?,
        /** Whether the owning calendar allows modifying events (shows edit/delete). */
        val canModify: Boolean = false,
        /**
         * The event belongs to a managed special-dates calendar: its editable
         * fields (reminders, notes) can still be edited, but delete is hidden —
         * the sync would just resurrect it. Contact dates are removed at the source.
         */
        val isManaged: Boolean = false,
        /** Phone numbers to link in the title, location and description. */
        val phoneLinks: EventPhoneLinks = EventPhoneLinks(),
        /** The description as drawn — HTML rendered (#271); null when there is none. */
        val description: RichText? = null,
    ) : EventDetailUiState
}

/**
 * One-shot state of a delete request, separate from the screen state so a
 * failed delete leaves the loaded detail visible.
 */
sealed interface DeleteUiState {
    data object Idle : DeleteUiState
    data object Deleting : DeleteUiState
    data object Deleted : DeleteUiState
    /** WRITE_CALENDAR was revoked between the tap and the provider call. */
    data object NeedsPermission : DeleteUiState
    data object Failed : DeleteUiState
}
