package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.nextMoveToken
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** One week row's live geometry, republished on every layout while it is on screen. */
class MonthRowGeometry(
    val days: List<LocalDate>,
    /** The row's day-column box — the space chip offsets are measured in. */
    val cell: LayoutCoordinates,
    /** The lane band inside that box, where the chips themselves are seated. */
    val band: LayoutCoordinates?,
    val columnWidthPx: Float,
    val laneHeightPx: Float,
    val laneCount: Int,
    /**
     * Whether the columns are laid out right-to-left. Pointer coordinates are
     * never mirrored but the grid is, so the mapping has to flip with it.
     */
    val isRtl: Boolean,
    /** What this row currently draws in [lane] of column `col`, or null. */
    val chipAt: (col: Int, lane: Int) -> EventInstance?,
    /** The column the chip in [lane] of column `col` is drawn from. */
    val chipStart: (col: Int, lane: Int) -> Int,
) {
    /** Root top-left of the chip seated in [lane] of column [col]. */
    fun seat(col: Int, lane: Int): Offset? {
        val band = band?.takeIf { it.isAttached } ?: return null
        val origin = band.positionInRoot()
        val column = if (isRtl) days.lastIndex - col else col
        return Offset(origin.x + column * columnWidthPx, origin.y + lane * laneHeightPx)
    }

    /**
     * Where this row seats [event] on [date], or null when it doesn't hold it —
     * the day isn't in this week, or the chip went into the day's "+N" overflow.
     */
    fun seatOf(event: EventInstance, date: LocalDate): Offset? {
        val (col, lane) = chipSeat(days, laneCount, chipAt, chipStart, event, date) ?: return null
        return seat(col, lane)
    }
}

/**
 * The column and lane a row draws [event]'s chip in, given it covers [date] —
 * null when the row seats no such chip. The column is the *left end* of the bar
 * within this row, not [date]'s own: a bar spanning several days is drawn once,
 * from its first column, so an event dropped onto the second day it covers is
 * seated a column (or more) to the left of where it landed (#253).
 */
internal fun chipSeat(
    days: List<LocalDate>,
    laneCount: Int,
    chipAt: (col: Int, lane: Int) -> EventInstance?,
    chipStart: (col: Int, lane: Int) -> Int,
    event: EventInstance,
    date: LocalDate,
): Pair<Int, Int>? {
    val col = days.indexOf(date).takeIf { it >= 0 } ?: return null
    val lane = (0 until laneCount).firstOrNull { lane ->
        chipAt(col, lane)?.let { isSameEvent(it, event) } == true
    } ?: return null
    return chipStart(col, lane) to lane
}

/**
 * Whether [chip] is the moved event [moved] as the grid now holds it. Neither id
 * alone will do: re-read instances get new instance ids, and a single-occurrence
 * move writes a new event id — the title survives both. A *blank* title is no
 * evidence at all, though: every untitled event carries one, so matching on it
 * made any two of them the same event (#253).
 */
internal fun isSameEvent(chip: EventInstance, moved: EventInstance): Boolean =
    chip.eventId == moved.eventId ||
        (moved.title.isNotBlank() && chip.title == moved.title)

/** A chip in flight, in root coordinates so it can be drawn in an overlay. */
data class MonthChipDrag(
    val event: EventInstance,
    /** The day whose column the chip was grabbed in — the drag is a delta from it. */
    val grabDate: LocalDate,
    val targetDate: LocalDate?,
    val topLeftInRoot: Offset,
    val sizePx: IntSize,
    /**
     * The start time of the chip this lifted off, already formatted (#219).
     * Carried rather than re-derived: the overlay has no row to ask whether the
     * bar was carried in from the previous week, and no zone the grid laid out
     * in. Null where the source chip had no time to show.
     */
    val time: String? = null,
)

/** A drop parked by a newer pickup while its write still runs — see [MonthDragController.held]. */
data class HeldChip(
    val copy: MonthChipDrag,
    val token: Long,
    val handedOver: Boolean = false,
    /** Where the grid now draws the drop, in root coordinates, once a row seats it. */
    val seat: Offset? = null,
)

/** Where a finished chip drag asks its event to go, as a whole-day shift. */
data class MonthChipDrop(
    val event: EventInstance,
    val grabDate: LocalDate,
    val targetDate: LocalDate,
    /** The drop's identity, which its held copy waits on — see [MonthDragController.settledToken]. */
    val token: Long,
)

/**
 * Hoisted drag state for the month grid (#68). Every visible week row registers
 * itself so a chip can be carried across rows, the target resolved against
 * whichever registered row the finger is over. Rows are keyed by an identity
 * token rather than a date: the continuous style can show the same week twice.
 */
@Stable
class MonthDragController {
    private val rows = LinkedHashMap<Any, MonthRowGeometry>()

    var drag: MonthChipDrag? by mutableStateOf(null)
        private set

    /**
     * A dropped chip, held on the day it landed on while the write runs — the
     * grid behind it still shows the old day until the query re-reads.
     */
    var settling: MonthChipDrag? by mutableStateOf(null)
        private set

    /** How many real drops this controller has handed back. */
    var drops by mutableIntStateOf(0)
        private set

    /**
     * Which chip is lifted, and whether anything is. Separate snapshot state from
     * [drag], which changes every frame, so the chips and rows that only need
     * "am I the ghost" don't recompose that often. Stays set through [settling].
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    /**
     * Where the grid itself now draws the drop, in root coordinates — published
     * by whichever week row ends up holding it. The copy is dropped wherever the
     * finger was while the grid seats it in a lane, so without this it would
     * cross-fade across the gap instead of gliding onto its seat.
     */
    var settledInRoot: Offset? by mutableStateOf(null)
        private set

    /**
     * Whether the drop's own chip is still standing in for the copy. Cleared at
     * [handOver] rather than [release], or the chip stays dim under a copy that
     * has already faded and brightens afterwards: a visible dip.
     */
    private var settledGhost: Boolean by mutableStateOf(false)

    /** Whether a finger is on a chip right now — [settling] is not dragging. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /**
     * This controller's recent drops by token, kept past [release] for
     * [beginUndo] — the confirmation chip carries Undo long after the copy is
     * gone, and it may be any of them the chip offers to undo.
     */
    private val undoable = LinkedHashMap<Long, MonthChipDrag>()

    /**
     * Earlier drops still being written when another chip was picked up. They
     * keep their copy and their ghost until their own token settles, rather than
     * vanishing and flashing back on the old day (#494).
     */
    var held: List<HeldChip> by mutableStateOf(emptyList())
        private set

    /** The token of the drop (or undo) [settling] is waiting on. */
    var settledToken: Long = 0L
        private set

    private var event: EventInstance? = null
    private var grabDate: LocalDate? = null
    private var time: String? = null
    private var grab = Offset.Zero
    private var pointer = Offset.Zero
    private var sizePx = IntSize.Zero

    fun putRow(token: Any, geometry: MonthRowGeometry) {
        rows[token] = geometry
    }

    fun removeRow(token: Any) {
        rows.remove(token)
    }

    fun begin(
        event: EventInstance,
        grabDate: LocalDate,
        pointerInRoot: Offset,
        chipInRoot: Offset,
        size: IntSize,
        time: String? = null,
    ) {
        this.event = event
        this.grabDate = grabDate
        this.time = time
        parkSettling()
        isDragging = true
        liftedInstanceId = event.instanceId
        grab = pointerInRoot - chipInRoot
        pointer = pointerInRoot
        sizePx = size
        recompute()
    }

    fun move(pointerInRoot: Offset) {
        pointer = pointerInRoot
        recompute()
    }

    fun cancel() {
        event = null
        grabDate = null
        time = null
        isDragging = false
        liftedInstanceId = null
        drag = null
        settling = null
        settledInRoot = null
        settledGhost = false
    }

    /**
     * Whether [event]'s chip belongs to a held drop and must stay a ghost: its
     * source, or once a row seats it, the chip the grid draws on its target day.
     */
    fun isHeldChip(event: EventInstance, days: List<LocalDate>?): Boolean = held.any { parked ->
        !parked.handedOver && (
            parked.copy.event.instanceId == event.instanceId ||
                (
                    parked.seat != null && days != null && parked.copy.targetDate in days &&
                        isSameEvent(event, parked.copy.event)
                    )
            )
    }

    /** Move a still-settling drop to [held] before a new pickup takes the slot. */
    private fun parkSettling() {
        val landed = settling
        if (landed != null) held = held + HeldChip(landed, settledToken)
        settling = null
        settledInRoot = null
        settledGhost = false
    }

    /** Stop ghosting the held drop [token]'s source while its copy fades. */
    fun handOverHeld(token: Long) {
        held = held.map { if (it.token == token) it.copy(handedOver = true) else it }
    }

    /** Drop the held copy [token] once it has faded. */
    fun releaseHeld(token: Long) {
        held = held.filterNot { it.token == token }
    }

    /**
     * Whether [days] holds the chip the grid has drawn for the settled drop —
     * the seat the copy is on its way to, which must ghost until it gets there.
     */
    fun isSettledChip(event: EventInstance, days: List<LocalDate>?): Boolean {
        val landed = settling?.takeIf { settledGhost } ?: return false
        if (days == null || landed.targetDate !in days) return false
        return isSameEvent(event, landed.event)
    }

    /**
     * Ask the row keyed [token] whether it now seats the settled chip. Both
     * copies of a week the continuous style shows twice will answer; the seat
     * nearest where the chip was let go is the one the finger was over.
     */
    fun noteSettled(token: Any) {
        val row = rows[token]
        if (row != null && held.any { it.seat == null }) {
            held = held.map { parked ->
                val target = parked.copy.targetDate
                if (parked.seat != null || target == null) parked else parked.copy(seat = row.seatOf(parked.copy.event, target))
            }
        }
        val landed = settling ?: return
        val target = landed.targetDate ?: return
        val at = rows[token]?.seatOf(landed.event, target) ?: return
        val current = settledInRoot
        val closer = current == null ||
            abs(at.y - landed.topLeftInRoot.y) < abs(current.y - landed.topLeftInRoot.y)
        if (closer) settledInRoot = at
    }

    /**
     * Put a chip on the journey an undo's inverse write is about to make, so it
     * travels back rather than teleporting. Writes nothing itself. False when
     * there is no drop of this controller's left to undo, or the grid doesn't
     * seat the moved event where the chip would have to start from.
     */
    fun beginUndo(token: Long, dropToken: Long): Boolean {
        val last = undoable.remove(dropToken) ?: return false
        parkSettling()
        val from = last.targetDate ?: return false
        val at = rows.values.firstNotNullOfOrNull { it.seatOf(last.event, from) } ?: return false
        settling = last.copy(grabDate = from, targetDate = last.grabDate, topLeftInRoot = at)
        liftedInstanceId = last.event.instanceId
        settledGhost = true
        settledInRoot = null
        settledToken = token
        return true
    }

    /**
     * End the drag, handing back where it landed — null when it never resolved,
     * or when it landed back on the day it came from. A real drop keeps its chip
     * on the target day as [settling] until [release], slid over to that day's
     * column so it lands where the grid is about to draw it.
     */
    fun finish(): MonthChipDrop? {
        val landed = drag
        val left = landed?.targetDate?.let(::columnLeft)
        cancel()
        val target = landed?.targetDate?.takeIf { it != landed.grabDate } ?: return null
        settling = landed.copy(
            topLeftInRoot = Offset(left ?: landed.topLeftInRoot.x, landed.topLeftInRoot.y),
        )
        liftedInstanceId = landed.event.instanceId
        settledGhost = true
        settledToken = nextMoveToken()
        undoable[settledToken] = requireNotNull(settling)
        if (undoable.size > UNDOABLE_KEPT) undoable.remove(undoable.keys.first())
        drops++
        return MonthChipDrop(landed.event, landed.grabDate, target, settledToken)
    }

    /**
     * Stop ghosting the source while the copy is still dissolving — un-ghosting
     * at [release] instead leaves the chip dim and then brightens it.
     */
    fun handOver() {
        liftedInstanceId = null
        settledGhost = false
    }

    /** Drop the copy, once it has faded into the grid's own chip. */
    fun release() {
        settling = null
        settledInRoot = null
        handOver()
    }

    /** Root x of [date]'s column, in whichever visible row shows that day. */
    private fun columnLeft(date: LocalDate): Float? = rows.values.firstNotNullOfOrNull { row ->
        val index = row.days.indexOf(date).takeIf { it >= 0 } ?: return@firstNotNullOfOrNull null
        val bounds = row.cell.takeIf { it.isAttached }?.boundsInRoot()
            ?: return@firstNotNullOfOrNull null
        val column = if (row.isRtl) row.days.lastIndex - index else index
        bounds.left + column * row.columnWidthPx
    }

    private fun recompute() {
        val event = event ?: return
        val grabbed = grabDate ?: return
        val resolved = rows.values.firstNotNullOfOrNull { row ->
            val bounds = row.cell.takeIf { it.isAttached }?.boundsInRoot()
                ?: return@firstNotNullOfOrNull null
            if (!bounds.contains(pointer) || row.columnWidthPx <= 0f) {
                null
            } else {
                val column = ((pointer.x - bounds.left) / row.columnWidthPx)
                    .toInt()
                    .coerceIn(0, row.days.lastIndex)
                row.days[if (row.isRtl) row.days.lastIndex - column else column]
            }
        }
        drag = MonthChipDrag(
            event = event,
            grabDate = grabbed,
            // Off the grid entirely: keep the last cell it was over, so a
            // wobble past the edge doesn't drop the target.
            targetDate = resolved ?: drag?.targetDate,
            topLeftInRoot = pointer - grab,
            sizePx = sizePx,
            time = time,
        )
    }
}

@Composable
fun rememberMonthDragController(): MonthDragController {
    return remember { MonthDragController() }
}

val LocalMonthDrag = compositionLocalOf<MonthDragController?> { null }

/** How many recent drops a month controller keeps for an undo to carry back. */
private const val UNDOABLE_KEPT = 8
