package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.instantIn
import de.jeanlucmakiola.calendula.domain.repeatedHourOffsetAt
import de.jeanlucmakiola.calendula.domain.repicked
import de.jeanlucmakiola.calendula.domain.toFormTime
import de.jeanlucmakiola.calendula.domain.resolveIn
import de.jeanlucmakiola.calendula.ui.week.MINUTES_PER_DAY
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import de.jeanlucmakiola.calendula.ui.week.minutesPast
import de.jeanlucmakiola.calendula.ui.week.wallClockEndMinutes
import de.jeanlucmakiola.calendula.ui.week.wallClockMinutes
import de.jeanlucmakiola.floret.identity.LocalFloretDarkTheme
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.plus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Start granularity a dragged block snaps to, matching the big calendar apps. */
const val DRAG_SNAP_MINUTES: Int = 15

/** How far below a strip drag's timed block top the finger holds it, in minutes. */
private const val STRIP_GRAB_MINUTES = 15

/** How close to a timeline edge the finger must get before the view scrolls. */
private val AUTO_SCROLL_EDGE = 64.dp

/** Fastest auto-scroll step, per frame, right at the edge. */
private val AUTO_SCROLL_STEP = 16.dp

/** A block being dragged, in root coordinates so it can be drawn in an overlay. */
data class TimelineDrag(
    val event: EventInstance,
    val date: LocalDate,
    /**
     * Where the dragged *block's* top edge now sits, in minutes from [date]'s
     * midnight. The gesture's own quantity — what snaps, what the haptics tick
     * on, and what the drop is expressed in. Not the event's start: for the tail
     * of an event that began the day before, the two are a clip offset apart.
     */
    val startMin: Int,
    /** The event's new start, from [date]'s midnight — negative if it began earlier. */
    val eventStartMin: Int,
    val eventSpanMin: Int,
    /** The event as the grid would draw it, one piece per day column it covers. */
    val pieces: List<TimelineDragPiece>,
    /**
     * Lines the block the finger picked up drew its title over, zero for one too
     * short to have drawn it at all. The floating copy is that block's size, so
     * it has to spend its height the same way or the text re-wraps under the
     * finger and snaps back on drop (#267).
     */
    val titleLines: Int,
    /**
     * True when [pieces] is only [edgeDragSlice]'s stand-in — the event has left
     * every column this timeline shows. A hint under the finger, not where the
     * drop lands, so it is dropped once the gesture ends ([finish]).
     */
    val edgeOnly: Boolean = false,
    /**
     * The minute of its day the gutter badge names — the top edge, or for a
     * bottom-edge resize the end being dragged (#141).
     */
    val markerMin: Int = startMin,
) {
    /**
     * What the snap haptics key off: one tick per changed slot, not per frame.
     * Carries the length, which is all a bottom-edge resize changes.
     */
    val slot: Triple<LocalDate, Int, Int> get() = Triple(date, startMin, eventSpanMin)
}

/**
 * An event lifted off the all-day strip: held over the strip it moves [dayShift]
 * days and stays whole-day, held over the time grid it becomes a timed block.
 */
data class StripDrag(val event: EventInstance, val dayShift: Int = 0, val overGrid: Boolean = false)

/** Where a strip drag asks its event to go. */
sealed interface StripDrop {
    val event: EventInstance

    /** The drop's identity, which its held copy waits on. */
    val token: Long

    data class Days(override val event: EventInstance, val days: Int, override val token: Long) : StripDrop

    data class Timed(
        override val event: EventInstance,
        val start: Instant,
        val end: Instant,
        override val token: Long,
    ) : StripDrop
}

/** The event a long press left selected (#141). */
data class TimelineSelection(
    val event: EventInstance,
    /** Set from a move or resize of it until the grid shows where it landed. */
    val moved: Boolean = false,
    /** Selected by letting go of a lifted block, which then keeps that lift as it is. */
    val fromLift: Boolean = false,
)

/**
 * One day column's share of a dragged event, ready to draw. Both halves of an
 * event crossing midnight are previewed, whichever half the finger picked up:
 * showing only the held one made the event read as growing rather than moving,
 * since the day beside it still stood at the block's old extent (#253).
 */
data class TimelineDragPiece(
    val topLeftInRoot: Offset,
    val sizePx: IntSize,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/**
 * How a run of [spanMin] minutes beginning [eventStartMin] minutes into a day —
 * negative when it began on an earlier one — falls across day columns, as
 * offsets from that day. Every slice but the first and last is a whole day, and
 * the flags say which edges are cuts rather than the event's own ends.
 *
 * [within] bounds the day offsets that have a column to draw in, so a very long
 * event doesn't slice days no one can see.
 */
internal fun dragSlices(
    eventStartMin: Int,
    spanMin: Int,
    within: IntRange? = null,
): List<DragSlice> {
    val span = spanMin.coerceAtLeast(0)
    val end = eventStartMin + span
    val days = dragDayRange(eventStartMin, spanMin)
    val first = days.first
    val last = days.last
    val from = maxOf(first, within?.first ?: first)
    val to = minOf(last, within?.last ?: last)
    if (from > to) return emptyList()
    return (from..to).map { day ->
        val dayStart = day * MINUTES_PER_DAY
        val sliceStart = maxOf(eventStartMin - dayStart, 0)
        val sliceEnd = minOf(end - dayStart, MINUTES_PER_DAY)
        DragSlice(
            dayOffset = day,
            startMin = sliceStart,
            spanMin = sliceEnd - sliceStart,
            continuesBefore = day > first,
            continuesAfter = day < last,
        )
    }
}

/**
 * The one piece to draw when a drag has carried the whole event clear of the
 * columns this timeline shows, so [dragSlices] has nothing to hand back — where a
 * short clipped tail lands at its [dragFloorMin]: the event then belongs to the
 * day before, which Day view has no column for, and the copy would blink out from
 * under the finger mid-gesture. Pinned to the top of the nearest visible column
 * at minimum height instead, cut at that edge, so it reads as having slid off the
 * top. Null whenever a real slice exists. The drop is unaffected either way — it
 * reads [TimelineDrag.startMin], not the pieces.
 */
internal fun edgeDragSlice(eventStartMin: Int, spanMin: Int, within: IntRange): DragSlice? {
    if (dragDayRange(eventStartMin, spanMin).last >= within.first) return null
    return DragSlice(
        dayOffset = within.first,
        startMin = 0,
        spanMin = 0,
        continuesBefore = true,
        continuesAfter = false,
    )
}

/**
 * The day offsets a run of [spanMin] minutes beginning [eventStartMin] covers,
 * before any clipping to what a timeline shows. One rule, so [dragSlices] and
 * [edgeDragSlice] can't drift apart on where an event's days begin and end.
 */
private fun dragDayRange(eventStartMin: Int, spanMin: Int): IntRange {
    val end = eventStartMin + spanMin.coerceAtLeast(0)
    val first = eventStartMin.floorDiv(MINUTES_PER_DAY)
    // A zero-length event still has a block, on the one day it sits in.
    return first..maxOf((end - 1).floorDiv(MINUTES_PER_DAY), first)
}

/**
 * How far above its day's midnight a block's top edge may be dragged. Zero for
 * an ordinary block, whose top edge is the event's own start. A tail clipped at
 * midnight has to go lower: its top edge *is* midnight and stays there however
 * much earlier the event moves, so pinning it at zero made dragging the second
 * half earlier a no-op (#253). It may rise until the event's end reaches one
 * snap step into this day — or by a single step, for a tail too short to afford
 * even that, which then leaves the day for the one before it.
 */
internal fun dragFloorMin(clipOffsetMin: Int, eventSpanMin: Int): Int =
    if (clipOffsetMin > 0) {
        minOf(-DRAG_SNAP_MINUTES, DRAG_SNAP_MINUTES + clipOffsetMin - eventSpanMin)
    } else {
        0
    }

/**
 * Where one dragged piece's top edge sits below its column's top, and how tall
 * it is drawn, at [hourPx] to the hour. [insetPx] is the half-gap a block seated
 * in the hour grid gives up at each end, zero with the grid off — taken off the
 * copy exactly as the grid takes it off the block, or the copy is a gap taller
 * than the block it lifted off and spends the difference on a time label that
 * block had no room for (#267). An end cut at midnight keeps none of it, as the
 * block's own squared-off edge does.
 */
internal fun dragPieceBounds(
    slice: DragSlice,
    hourPx: Float,
    insetPx: Float,
): DragPieceBounds {
    val top = if (slice.continuesBefore) 0f else insetPx
    val bottom = if (slice.continuesAfter) 0f else insetPx
    val span = renderedSpan(slice.startMin, slice.startMin + slice.spanMin, slice.continuesAfter)
    val full = (span.bottomMin - span.topMin) / 60f * hourPx
    return DragPieceBounds(
        top = span.topMin / 60f * hourPx + top,
        height = (full - top - bottom).coerceAtLeast(0f),
    )
}

/** A piece's placement within its column — see [dragPieceBounds]. */
internal data class DragPieceBounds(val top: Float, val height: Float)

/** One day's share of a dragged event, before it is placed on screen. */
internal data class DragSlice(
    val dayOffset: Int,
    val startMin: Int,
    val spanMin: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/**
 * Where a finished drag asks its event to go. [date]/[startMin] are where the
 * dragged *block's* top edge landed, which is the event's own start only for a
 * block that isn't clipped — see [clipOffsetMin] and [startInstant].
 */
data class TimelineDrop(
    val event: EventInstance,
    val date: LocalDate,
    val startMin: Int,
    /** Minutes from the event's start to the dragged block's top edge. */
    val clipOffsetMin: Int,
    /** The drop's identity, which its held copy waits on. */
    val token: Long,
)

/** The id a drafted block's stand-in event carries, which no provider row has (#153). */
private const val DRAFT_ID = Long.MIN_VALUE

/** Whether this is the stand-in for a block drafted on an empty slot, not a real event. */
val EventInstance.isDraft: Boolean get() = instanceId == DRAFT_ID

/** A drafted block's stand-in event, [minutes] long from [start]. */
fun draftEvent(title: String, color: Int, start: Instant, minutes: Int): EventInstance = EventInstance(
    instanceId = DRAFT_ID,
    eventId = DRAFT_ID,
    calendarId = DRAFT_ID,
    title = title,
    start = start,
    end = start + minutes.minutes,
    isAllDay = false,
    color = color,
    location = null,
)

/** Where a long press [minutes] down the column starts its draft, snapped down to the slot. */
internal fun draftStartMin(minutes: Float): Int =
    (minutes / DRAG_SNAP_MINUTES).toInt().times(DRAG_SNAP_MINUTES)
        .coerceIn(0, MINUTES_PER_DAY - DRAG_SNAP_MINUTES)

/** The part of [draft] on [date] as a full-width block, or null if none of it falls there. */
internal fun draftBlock(draft: EventInstance, date: LocalDate, zone: TimeZone): TimedBlock? {
    // Clipped to the day the way the grid lays out events, so it's drawn the same across a DST change (#387).
    // The end is exclusive, so a draft ending at midnight belongs to the day before.
    val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    val start = maxOf(draft.start, date.atStartOfDayIn(zone))
    val end = minOf(draft.end, dayEnd)
    if (end <= start) return null
    val startMin = wallClockMinutes(start, date, zone)
    val endMin = wallClockEndMinutes(startMin, start, end, date, zone).coerceAtMost(MINUTES_PER_DAY)
    return TimedBlock(draft, startMin, endMin, lane = 0, laneCount = 1, continuesAfter = draft.end > dayEnd)
}

/** Which end of an event a resize handle moves (#141). */
enum class ResizeEdge { Top, Bottom }

/** A drop parked by a newer pickup while its write still runs — see [TimelineDragController.held]. */
data class HeldDrop(
    val copy: TimelineDrag,
    val token: Long,
    val instanceId: Long,
    /** The event row and start the drop came from, as [TimelineDragController.ghosts] matches them. */
    val origin: Pair<Long, Instant>,
    /** A resize's new end, which has to have landed too before the drop counts as seated. */
    val end: Instant?,
    /** Whether the grid now draws the drop at its landing slot. */
    val seated: Boolean = false,
    val handedOver: Boolean = false,
) {
    fun ghosts(block: TimedBlock): Boolean = block.event.instanceId == instanceId ||
        (block.event.eventId == origin.first && block.event.start == origin.second)
}

/** A strip drop parked by a newer one while its write still runs — see [TimelineDragController.heldStrips]. */
data class HeldStrip(val drag: StripDrag, val token: Long, val seated: Boolean = false)

/** An event's new start and end, as a resize leaves them. */
data class ResizedSpan(val start: Instant, val end: Instant)

/** Where a finished resize asks its event to begin and end. */
data class TimelineResizeDrop(
    val event: EventInstance,
    val start: Instant,
    val end: Instant,
    /** The drop's identity, which its held copy waits on. */
    val token: Long,
)

/**
 * [event] resized by its [edge] to the span [startMin]..+[spanMin] from [date]'s
 * midnight. Only the held edge moves. The other keeps its own instant rather than
 * a round trip through the grid, where an end in the fall-back hour's repeat is
 * drawn to that hour's end, not at its own time (#387). In a repeated hour, the
 * held edge takes the pass that matches the direction it was dragged from where
 * it was drawn (see [repicked]). An end takes a pass the grid draws at the drop
 * point, so the block settles under the finger.
 */
internal fun resizedSpan(
    event: EventInstance,
    edge: ResizeEdge?,
    date: LocalDate,
    startMin: Int,
    spanMin: Int,
    zone: TimeZone,
): ResizedSpan {
    val drawn = wallClockSpan(event, zone)
    return ResizedSpan(
        start = if (edge == ResizeEdge.Top) {
            event.start.toFormTime(zone).repicked(
                wallClockTime(date, startMin),
                zone,
                shownAt = wallClockTime(drawn.day, drawn.startMin),
                notAfter = event.end,
            ).instantIn(zone)
        } else {
            event.start
        },
        end = if (edge == ResizeEdge.Bottom) {
            val picked = wallClockTime(date, startMin + spanMin)
            val pickedMin = (picked.date.toEpochDays() - drawn.day.toEpochDays()).toInt() * MINUTES_PER_DAY +
                picked.time.toSecondOfDay() / 60
            event.end.toFormTime(zone).repicked(
                picked,
                zone,
                shownAt = wallClockTime(drawn.day, drawn.endMin),
                notBefore = event.start,
                drawable = { wallClockEndMinutes(drawn.startMin, event.start, it, drawn.day, zone) == pickedMin },
            ).instantIn(zone)
        } else {
            event.end
        },
    )
}

/**
 * The span after dragging [edge] to [edgeMin] in day [edgeDay], in minutes from the
 * start day's midnight. Snapped, and at least one snap step long.
 */
internal fun resizedSpan(
    edge: ResizeEdge,
    startMin: Int,
    endMin: Int,
    edgeMin: Float,
    edgeDay: Int? = null,
): Pair<Int, Int> {
    // An edge dragged into a day past the other end changes nothing.
    if (edgeDay != null) {
        val endDay = (endMin - 1).floorDiv(MINUTES_PER_DAY).coerceAtLeast(0)
        val wrongWay = when (edge) {
            ResizeEdge.Top -> edgeDay > endDay
            ResizeEdge.Bottom -> edgeDay < startMin.floorDiv(MINUTES_PER_DAY)
        }
        if (wrongWay) return startMin to endMin
    }
    val snapped = snapToSlot(edgeMin)
    return when (edge) {
        ResizeEdge.Top -> minOf(snapped, endMin - DRAG_SNAP_MINUTES) to endMin
        ResizeEdge.Bottom -> startMin to maxOf(snapped, startMin + DRAG_SNAP_MINUTES)
    }
}

/** [minutes] rounded to the nearest [DRAG_SNAP_MINUTES] slot. */
internal fun snapToSlot(minutes: Float): Int = (minutes / DRAG_SNAP_MINUTES).roundToInt() * DRAG_SNAP_MINUTES

/** [minutes] snapped, kept at [held] until three quarters of a step away. */
internal fun stickySnap(minutes: Float, held: Int?): Int {
    if (held != null && kotlin.math.abs(minutes - held) < DRAG_SNAP_MINUTES * 0.75f) return held
    return snapToSlot(minutes)
}

/** How far past its column's side the finger has to go before a resize changes day. */
private const val COLUMN_HYSTERESIS = 0.25f

/** The column under [position], kept at [held] until [COLUMN_HYSTERESIS] into another. */
internal fun stickyColumn(position: Float, held: Int?, lastIndex: Int): Int {
    val under = position.toInt().coerceIn(0, lastIndex)
    if (held == null) return under
    val stays = position >= held - COLUMN_HYSTERESIS && position < held + 1 + COLUMN_HYSTERESIS
    return if (stays) held else under
}

/**
 * The timeline's live geometry, republished on every layout. Plain fields rather
 * than snapshot state: it changes on every scroll frame, and the drag loop reads
 * it per frame anyway.
 */
class TimelineGeometry {
    /** The day-columns row — scrolling *content*, so its root position folds in the scroll. */
    var grid: LayoutCoordinates? = null

    /** The scroll viewport, for the edge that triggers auto-scrolling. */
    var viewport: LayoutCoordinates? = null
    var scroll: ScrollState? = null
    var hourPx: Float = 0f

    /** Column pitch — one column plus the gap after it. */
    var columnWidthPx: Float = 0f
    var columnGapPx: Float = 0f
    var edgePx: Float = 0f
    var stepPx: Float = 0f
    var days: List<LocalDate> = emptyList()

    /**
     * The half-gap a block gives up at each uncut end when the hour grid seats
     * it in a cell, zero with the grid off. The floating copy gives up the same,
     * so it is the size of the block it lifted off (#267).
     */
    var blockInsetPx: Float = 0f

    /**
     * Whether the columns are laid out right-to-left. Pointer coordinates are
     * never mirrored but the grid is, so the mapping has to flip with it.
     */
    var isRtl: Boolean = false
}

/**
 * Hoisted drag state for one timeline (#68). It lives above the pager, so a
 * page change mid-drag can't strand a ghost, and the
 * block it renders is drawn in an overlay, clear of the day column's clip.
 */
@Stable
class TimelineDragController {
    val geometry = TimelineGeometry()

    var drag: TimelineDrag? by mutableStateOf(null)
        private set

    /**
     * A dropped block, held at the slot it landed on while the write runs — the
     * grid behind it still shows the old time until the query re-reads.
     */
    var settling: TimelineDrag? by mutableStateOf(null)
        private set

    /** How many real drops this controller has handed back. */
    var drops by mutableIntStateOf(0)
        private set

    /**
     * Which block is lifted, and whether anything is. Separate snapshot state
     * from [drag], which changes every frame, so blocks that only need "am I the
     * ghost" don't recompose that often. Stays set through [settling].
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    /**
     * The event row and start instant the settled drop came from. The instance id
     * alone can't identify the source for the length of the write: the provider
     * regenerates `Instances` rows, so a re-read carrying the *old* time can
     * arrive under a new instance id. Matching the start instant rather than a
     * single day and slot keeps *both* halves of an event that crosses midnight
     * ghosted until the write lands (#253).
     */
    private var settledOrigin: Pair<Long, Instant>? by mutableStateOf(null)

    /**
     * Earlier drops still being written when another block was picked up. They
     * keep their copy and their ghost until their own token settles, rather than
     * vanishing and flashing back at the old slot (#494).
     */
    var held: List<HeldDrop> by mutableStateOf(emptyList())
        private set

    /** How long a block drafted on an empty slot starts out, in minutes (#153). */
    var draftMinutes: Int = SettingsPrefs.DEFAULT_EVENT_DURATION

    /** The selected event, if any — see [TimelineSelection]. */
    var selection: TimelineSelection? by mutableStateOf(null)
        private set

    fun select(event: EventInstance, moved: Boolean = false, fromLift: Boolean = false) {
        if (selection?.event?.instanceId != event.instanceId) selectionCovered = false
        selection = TimelineSelection(event, moved, fromLift)
    }

    fun deselect() {
        if (selection?.event?.isDraft == true && settling?.event?.isDraft == true) release()
        selection = null
        selectionCovered = false
    }

    /** The events of the blocks on screen that show the selection; none for a while ends it. */
    val selectedCandidates = mutableStateListOf<EventInstance>()

    fun noteSelectedBlock(event: EventInstance, shown: Boolean) {
        if (shown) selectedCandidates.add(event) else selectedCandidates.remove(event)
    }

    /**
     * Once a move of the selected event has settled, point the selection at the
     * row it landed as. A new event id wins: a single moved occurrence is written
     * as an exception, and another occurrence of its series may share its time.
     */
    fun settleMovedSelection() {
        val current = selection?.takeIf { it.moved } ?: return
        if (isDragging || settling != null) return
        val landed = selectedCandidates.filter { it.instanceId != current.event.instanceId }
        // Only the original row left: the move was abandoned, and it still stands.
        val chosen = landed.firstOrNull { it.eventId != current.event.eventId }
            ?: landed.firstOrNull() ?: selectedCandidates.firstOrNull() ?: return
        selection = current.copy(event = chosen, moved = false)
    }

    /** Whether the selected block's bottom sits where the action bar would cover it. */
    var selectionCovered: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether [block] is the selected event: by instance, by series and start, and
     * just after a move by title and times, since a moved occurrence gets new ids.
     */
    fun isSelected(block: TimedBlock): Boolean {
        val current = selection ?: return false
        val selected = current.event
        val event = block.event
        return event.instanceId == selected.instanceId ||
            (event.eventId == selected.eventId && event.start == selected.start) ||
            (
                current.moved &&
                    event.calendarId == selected.calendarId &&
                    event.title.isNotBlank() &&
                    event.title == selected.title &&
                    event.start == selected.start &&
                    event.end == selected.end
                )
    }

    /** Point the selection at [block]'s event, so the bar acts on the row now there. */
    fun adoptSelection(block: TimedBlock) {
        val current = selection?.takeIf { !it.moved } ?: return
        if (current.event != block.event) selection = current.copy(event = block.event)
    }

    /** Whether a finger is on a block right now — [settling] is not dragging. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /** Whether the finger has moved since the pickup; only then may the view auto-scroll. */
    var pointerMoved: Boolean by mutableStateOf(false)
        private set

    /** Touch slop, past which the finger counts as having moved. */
    var slopPx: Float = 0f

    private var pickupPointer = Offset.Zero

    /** The end a settling resize asked for, which the grid has to show before it settles. */
    private var settledEnd: Instant? = null

    /**
     * Whether the grid itself now draws the settled drop at its landing slot.
     * The copy may only be handed back once this is true, or the source ghost
     * flashes back to full opacity in its old slot.
     */
    var settledOnGrid: Boolean by mutableStateOf(false)
        private set

    /** Whether the settling drop is the selected event, whose block lands still lifted. */
    var settlingSelected: Boolean = false
        private set

    /** The token of the drop [settling] waits on. */
    var settledToken: Long = 0L
        private set

    private var source: TimedBlock? = null
    private var grab = Offset.Zero
    private var pointer = Offset.Zero

    /**
     * How far the picked-up block's top edge sits past the event's own start —
     * nonzero only for a block clipped at midnight, which is the tail of an
     * event that began the day before (#253). The gesture tracks the block the
     * finger is on; this is what turns where it lands back into an event start.
     */
    private var clipOffsetMin = 0
    private var dragZone: TimeZone? = null
    private var dragDuration = Duration.ZERO
    /** The dragged event's pass of a repeated hour, worked out once per drag (#387). */
    private var dragKept: UtcOffset? = null

    /** What the picked-up block drew its title over — see [TimelineDrag.titleLines]. */
    private var titleLines = 0

    /**
     * The slot the block already occupied when it was picked up — not the same
     * as its start, since the target snaps to the grid (09:07 lifts to 09:00).
     */
    private var originSlot: Triple<LocalDate, Int, Int>? = null

    /** The resize in progress, or null for a plain drag (#141). */
    private var resize: ResizeSession? = null

    /** The strip drag in progress; over the grid it drives [drag] through a timed stand-in. */
    var stripDrag: StripDrag? by mutableStateOf(null)
        private set

    /** A strip drop held at its new days until the strip redraws the event there. */
    var stripSettling: StripDrag? by mutableStateOf(null)
        private set

    /** Whether the strip now draws [stripSettling] at its new days itself. */
    var stripSeated: Boolean by mutableStateOf(false)
        private set

    /** Strip drops parked by a newer strip drop while their writes still run (#494). */
    var heldStrips: List<HeldStrip> by mutableStateOf(emptyList())
        private set

    /** The token of the drop [stripSettling] waits on. */
    var stripToken: Long = 0L
        private set

    /** The shown day the finger picked the strip bar up on. */
    private var stripGrabIndex = 0

    /** Whether a strip drag over the grid has cleared its top edge, and may now scroll up. */
    private var stripScrollArmed = false

    fun begin(
        block: TimedBlock,
        clipOffsetMin: Int,
        zone: TimeZone,
        titleLines: Int,
        pointerInRoot: Offset,
        blockInRoot: Offset,
    ) {
        // The selected event keeps its selection through a drag; any other ends it.
        if (!isSelected(block)) deselect()
        source = block
        this.clipOffsetMin = clipOffsetMin
        dragZone = zone
        dragDuration = (block.event.end - block.event.start).coerceAtLeast(Duration.ZERO)
        dragKept = zone.repeatedHourOffsetAt(block.event.start)
        this.titleLines = titleLines
        parkSettling()
        isDragging = true
        liftedInstanceId = block.event.instanceId
        grab = pointerInRoot - blockInRoot
        pointer = pointerInRoot
        pickupPointer = pointerInRoot
        pointerMoved = false
        originSlot = null
        recompute()
        originSlot = drag?.slot
    }

    /**
     * Draft a block on an empty slot and lift it at once (#153). It is selected
     * from the start, so it keeps its handles and bar once the finger lets go.
     */
    fun beginDraft(block: TimedBlock, zone: TimeZone, pointerInRoot: Offset, blockInRoot: Offset) {
        deselect()
        select(block.event)
        begin(block, clipOffsetMin = 0, zone, titleLines = 1, pointerInRoot = pointerInRoot, blockInRoot = blockInRoot)
    }

    /**
     * Lift [event] off the all-day strip. Over the time grid it is dragged as a
     * block [draftMinutes] long, whose top sits a little above the finger.
     */
    fun beginStrip(event: EventInstance, zone: TimeZone, pointerInRoot: Offset) {
        val grid = geometry.grid?.takeIf { it.isAttached } ?: return
        if (geometry.columnWidthPx <= 0f || geometry.days.isEmpty()) return
        deselect()
        if (stripSettling?.event?.instanceId == event.instanceId) releaseStrip()
        stripScrollArmed = false
        val timed = event.copy(isAllDay = false, end = event.start + draftMinutes.minutes)
        source = TimedBlock(timed, 0, draftMinutes, lane = 0, laneCount = 1)
        clipOffsetMin = 0
        dragZone = zone
        dragDuration = draftMinutes.minutes
        dragKept = null
        titleLines = 1
        parkSettling()
        isDragging = true
        liftedInstanceId = event.instanceId
        grab = Offset(0f, geometry.hourPx * STRIP_GRAB_MINUTES / 60f)
        pointer = pointerInRoot
        pickupPointer = pointerInRoot
        pointerMoved = false
        originSlot = null
        stripGrabIndex = dayIndexAt(pointerInRoot.x, grid.positionInRoot().x)
        stripDrag = StripDrag(event)
    }

    /** The shown day under [x], clamped to the days on screen. */
    private fun dayIndexAt(x: Float, originX: Float): Int =
        column(((x - originX) / geometry.columnWidthPx).toInt().coerceIn(0, geometry.days.lastIndex))

    /** End a strip drag, handing back where it landed — null when it went nowhere. */
    fun finishStrip(): StripDrop? {
        val held = stripDrag ?: return null
        val landed = drag
        val zone = dragZone
        cancel()
        if (held.overGrid && landed != null && zone != null && !landed.edgeOnly) {
            val token = settle(landed)
            val start = dropStartInstant(landed.event, landed.date, landed.startMin, clipOffsetMin = 0, zone)
            return StripDrop.Timed(held.event, start, start + draftMinutes.minutes, token)
        }
        if (held.overGrid || held.dayShift == 0) return null
        // One still settling keeps its place on the strip until its own write lands (#494).
        stripSettling?.let { heldStrips = heldStrips + HeldStrip(it, stripToken, stripSeated) }
        stripSettling = held
        stripSeated = false
        stripToken = nextMoveToken()
        drops++
        return StripDrop.Days(held.event, held.dayShift, stripToken)
    }

    /** Let the held strip drop go, once the strip shows the event at its new days or the move failed. */
    fun releaseStrip() {
        stripSettling = null
        stripSeated = false
    }

    /** Note that the strip draws the settling drop at its new days. */
    fun seatStrip() {
        if (stripSettling != null) stripSeated = true
    }

    /** Note that the strip draws the parked strip drop [token] at its new days. */
    fun seatHeldStrip(token: Long) {
        if (heldStrips.any { it.token == token && !it.seated }) {
            heldStrips = heldStrips.map { if (it.token == token) it.copy(seated = true) else it }
        }
    }

    /** Let the parked strip drop [token] go. */
    fun releaseHeldStrip(token: Long) {
        heldStrips = heldStrips.filterNot { it.token == token }
    }

    /** Every strip drop still shown at its new days: the parked ones, then the latest. */
    fun stripPreviews(): List<StripDrag> = heldStrips.map { it.drag } + listOfNotNull(stripDrag ?: stripSettling)

    fun move(pointerInRoot: Offset) {
        pointer = pointerInRoot
        if (!pointerMoved && (pointerInRoot - pickupPointer).getDistance() > slopPx) pointerMoved = true
        recompute()
    }

    fun cancel() {
        resize = null
        stripDrag = null
        source = null
        clipOffsetMin = 0
        dragZone = null
        dragDuration = Duration.ZERO
        dragKept = null
        titleLines = 0
        isDragging = false
        pointerMoved = false
        liftedInstanceId = null
        originSlot = null
        drag = null
        settling = null
        settledOnGrid = false
        settledOrigin = null
        settledEnd = null
        settlingSelected = false
    }

    /**
     * Whether [block] is part of the event whose copy is in flight, and so must
     * stay a ghost: the instance the finger picked up, or once dropped whatever
     * the re-read now carries at the time it came from — both halves of it, for
     * an event that crosses midnight (#253).
     */
    fun ghosts(block: TimedBlock): Boolean {
        if (held.any { !it.handedOver && it.ghosts(block) }) return true
        if (liftedInstanceId == null) return false
        if (block.event.instanceId == liftedInstanceId) return true
        val (eventId, start) = settledOrigin ?: return false
        return block.event.eventId == eventId && block.event.start == start
    }

    /** Move a still-settling drop to [held] before a new pickup takes the slot. */
    private fun parkSettling() {
        val landed = settling
        val origin = settledOrigin
        if (landed != null && origin != null && !landed.event.isDraft) {
            held = held + HeldDrop(landed, settledToken, landed.event.instanceId, origin, settledEnd, settledOnGrid)
        }
        settling = null
        settledOnGrid = false
        settledOrigin = null
        settledEnd = null
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
     * What a day column now holds, so a settled drop can tell when the grid has
     * caught up with it. Matched on the landing slot plus either the event row
     * or its title, since a single-occurrence move writes a new `eventId`. A
     * blank title matches nothing: every untitled event has one (#253).
     */
    fun noteGrid(date: LocalDate, blocks: List<TimedBlock>) {
        if (held.any { !it.seated && drawsDrop(it.copy, it.end, date, blocks) }) {
            held = held.map { if (!it.seated && drawsDrop(it.copy, it.end, date, blocks)) it.copy(seated = true) else it }
        }
        val landed = settling ?: return
        if (settledOnGrid) return
        settledOnGrid = drawsDrop(landed, settledEnd, date, blocks)
    }

    /** Whether [blocks], [date]'s column, draws the drop [landed] where it landed. */
    private fun drawsDrop(landed: TimelineDrag, end: Instant?, date: LocalDate, blocks: List<TimedBlock>): Boolean {
        // Where this day would draw it, which for a drop clipped at midnight is
        // the day's own start rather than the edge the finger held (#253) — and
        // which for a tail dragged clear of its day is the day before it.
        val offset = (date.toEpochDays() - landed.date.toEpochDays()).toInt()
        val slice = dragSlices(landed.eventStartMin, landed.eventSpanMin, offset..offset)
            .firstOrNull() ?: return false
        return blocks.any { block ->
            block.startMin == slice.startMin &&
                // A resize may keep its start, so its end has to have landed too.
                (end == null || block.event.end == end) &&
                (
                    block.event.eventId == landed.event.eventId ||
                        (
                            landed.event.title.isNotBlank() &&
                                block.event.title == landed.event.title
                            )
                    )
        }
    }

    /**
     * End the drag, handing back where it landed — null when it never resolved,
     * or when it landed back on the slot it started from. A real drop keeps its
     * copy on the target as [settling] until [release].
     */
    fun finish(): TimelineDrop? {
        val landed = drag
        val origin = originSlot
        val clipOffset = clipOffsetMin
        cancel()
        if (landed == null || landed.slot == origin) return null
        // A draft dropped on a day off screen would vanish, so it stays where it was (#153).
        if (landed.edgeOnly && landed.event.isDraft) return null
        val token = settle(landed)
        return TimelineDrop(landed.event, landed.date, landed.startMin, clipOffset, token)
    }

    /**
     * Keep [landed]'s copy on the target as [settling] until [release]; [end] is a
     * resize's. Returns the drop's fresh token.
     */
    private fun settle(landed: TimelineDrag, end: Instant? = null): Long {
        // Landed on midnight to midnight, the event moves to the all-day strip (#296).
        val leavesGrid = !landed.event.isDraft && landed.eventSpanMin > 0 &&
            landed.eventStartMin.mod(MINUTES_PER_DAY) == 0 &&
            landed.eventSpanMin % MINUTES_PER_DAY == 0
        settlingSelected = selection?.event?.instanceId == landed.event.instanceId
        // An edge piece is a hint for the finger, not a preview of the landing
        // slot: the event belongs to a day this timeline has no column for, so
        // [noteGrid] can never seat it and it would sit at the top of today for
        // the whole settle timeout, long after the event moved off-screen.
        settling = if (landed.edgeOnly || leavesGrid) landed.copy(pieces = emptyList()) else landed
        liftedInstanceId = landed.event.instanceId
        settledOrigin = landed.event.eventId to landed.event.start
        settledToken = nextMoveToken()
        settledEnd = end
        drops++
        return settledToken
    }

    /**
     * Grab one edge of [block] to resize it (#141). The edge follows the finger
     * across days as a drag does; the other stays where it is.
     */
    fun beginResize(
        block: TimedBlock,
        edge: ResizeEdge,
        zone: TimeZone,
        titleLines: Int,
        pointerInRoot: Offset,
    ) {
        val span = wallClockSpan(block.event, zone)
        val session = ResizeSession(edge, span.day, span.startMin, span.endMin)
        source = block
        resize = session
        clipOffsetMin = 0
        this.titleLines = titleLines
        parkSettling()
        isDragging = true
        pointerMoved = true
        liftedInstanceId = block.event.instanceId
        pointer = pointerInRoot
        // Measured from the edge's own column: the handle overhangs into the next.
        val edgeDay = if (edge == ResizeEdge.Top) 0 else (session.endMin - 1).floorDiv(MINUTES_PER_DAY).coerceAtLeast(0)
        val edgeIndex = geometry.days.indexOf(span.day.plus(edgeDay, DateTimeUnit.DAY))
        session.column = edgeIndex.takeIf { it >= 0 }
            ?.let { column(it) }
        // The top handle sits on the drawn top, which a short block near midnight lifts (#483).
        val lift = if (edge == ResizeEdge.Top) block.startMin - block.renderedSpan().topMin else 0f
        session.grabMin = (pointerResizeMinutes(session) ?: 0f) + lift -
            if (edge == ResizeEdge.Top) session.startMin else session.endMin
        originSlot = null
        recompute()
        originSlot = drag?.slot
    }

    /** The slot the gesture would land on, changing once per snap rather than per frame. */
    private val landingSlot by derivedStateOf { drag?.slot }

    /**
     * The dragged event at the times letting go now would give it, worked out as
     * [finish] and [finishResize] do. Null while it is still on its own slot (#141).
     */
    fun preview(zone: TimeZone): EventInstance? {
        val (date, startMin, spanMin) = landingSlot?.takeIf { it != originSlot } ?: return null
        val event = source?.event ?: return null
        val edge = resize?.edge
        if (edge != null) {
            val drop = resizedSpan(event, edge, date, startMin, spanMin, zone)
            return event.copy(start = drop.start, end = drop.end)
        }
        val start = dropStartInstant(event, date, startMin, clipOffsetMin, zone)
        return event.copy(start = start, end = start + dragDuration)
    }

    /**
     * End a resize, handing back the event's new span — null when it never
     * resolved or came back to the length it started at. Settles as [finish] does.
     */
    fun finishResize(zone: TimeZone): TimelineResizeDrop? {
        val landed = drag
        val origin = originSlot
        val edge = resize?.edge
        cancel()
        if (landed == null || landed.slot == origin) return null
        val span = resizedSpan(landed.event, edge, landed.date, landed.eventStartMin, landed.eventSpanMin, zone)
        return TimelineResizeDrop(landed.event, span.start, span.end, settle(landed, end = span.end))
    }

    /**
     * Where the finger is, in minutes from the resized event's start day —
     * past 1440 in a later column, negative in an earlier one.
     */
    private fun pointerResizeMinutes(session: ResizeSession): Float? {
        val grid = geometry.grid?.takeIf { it.isAttached } ?: return null
        val hourPx = geometry.hourPx
        val columnPx = geometry.columnWidthPx
        val days = geometry.days
        if (hourPx <= 0f || columnPx <= 0f || days.isEmpty()) return null
        val origin = grid.positionInRoot()
        val held = stickyColumn(
            position = (pointer.x - origin.x) / columnPx,
            held = session.column,
            lastIndex = days.lastIndex,
        )
        session.column = held
        val dayOffset = (days[column(held)].toEpochDays() - session.day.toEpochDays()).toInt()
        session.edgeDay = dayOffset
        // Held to its column's midnight: the clearance under it is not the next day.
        val minutes = ((pointer.y - origin.y) / hourPx * 60f).coerceAtMost(MINUTES_PER_DAY.toFloat())
        return dayOffset * MINUTES_PER_DAY + minutes
    }

    private fun recomputeResize(session: ResizeSession) {
        val block = source ?: return
        val edge = session.edge
        val day = session.day
        val raw = pointerResizeMinutes(session) ?: return
        val slot = stickySnap(raw - session.grabMin, session.slot)
        session.slot = slot
        val (start, end) = resizedSpan(edge, session.startMin, session.endMin, slot.toFloat(), session.edgeDay)
        val days = geometry.days
        val origin = geometry.grid?.takeIf { it.isAttached }?.positionInRoot() ?: return
        // Which column the start day sits in, possibly off either end of the view.
        val startIndex = (day.toEpochDays() - days.first().toEpochDays()).toInt()
        val visible = -startIndex..days.lastIndex - startIndex
        val slices = dragSlices(start, end - start, within = visible)
        drag = TimelineDrag(
            event = block.event,
            date = day,
            startMin = start,
            eventStartMin = start,
            eventSpanMin = end - start,
            titleLines = titleLines,
            pieces = pieces(slices, startIndex, origin),
            edgeOnly = slices.isEmpty(),
            markerMin = if (edge == ResizeEdge.Top) {
                start.mod(MINUTES_PER_DAY)
            } else {
                end - (end - 1).floorDiv(MINUTES_PER_DAY) * MINUTES_PER_DAY
            },
        )
    }

    /** Note whether the action bar would cover the selected block's bottom handle (#141). */
    fun noteSelectedBottom(block: LayoutCoordinates, extraPx: Float, clearancePx: Float) {
        val viewport = geometry.viewport?.takeIf { it.isAttached } ?: return
        if (!block.isAttached) return
        val port = viewport.boundsInRoot()
        val bottom = block.positionInRoot().y + block.size.height + extraPx
        val covered = bottom > port.bottom - clearancePx
        if (covered != selectionCovered) selectionCovered = covered
    }

    /**
     * Stop ghosting the source — the grid draws the drop itself by now — while
     * the copy is still dissolving. Un-ghosting at [release] instead would leave
     * the block dim under the fading copy and then brighten it: a visible dip.
     */
    fun handOver() {
        liftedInstanceId = null
        settledOrigin = null
    }

    /** Drop the copy, once it has faded into the grid's own block. */
    fun release() {
        settling = null
        settledOnGrid = false
        settledEnd = null
        settlingSelected = false
        handOver()
    }

    /**
     * Re-derive the target from the pointer's *root* position. Recomputed rather
     * than accumulated from `positionChange()`, because a stationary finger emits
     * no move events while auto-scroll walks the content underneath it.
     */
    fun recompute() {
        resize?.let {
            recomputeResize(it)
            return
        }
        stripDrag?.let {
            recomputeStrip(it)
            return
        }
        recomputeDrag()
    }

    /** Over the strip the bar moves by whole days; past its bottom edge it is a timed block. */
    private fun recomputeStrip(held: StripDrag) {
        val grid = geometry.grid?.takeIf { it.isAttached } ?: return
        val viewport = geometry.viewport?.takeIf { it.isAttached } ?: return
        if (geometry.columnWidthPx <= 0f || geometry.days.isEmpty()) return
        if (pointer.y >= viewport.boundsInRoot().top) {
            stripDrag = held.copy(overGrid = true)
            recomputeDrag()
        } else {
            drag = null
            val shift = dayIndexAt(pointer.x, grid.positionInRoot().x) - stripGrabIndex
            stripDrag = held.copy(dayShift = shift, overGrid = false)
        }
    }

    private fun recomputeDrag() {
        val block = source ?: return
        val grid = geometry.grid?.takeIf { it.isAttached } ?: return
        val hourPx = geometry.hourPx
        val columnPx = geometry.columnWidthPx
        val days = geometry.days
        if (hourPx <= 0f || columnPx <= 0f || days.isEmpty()) return

        val origin = grid.positionInRoot()
        val rawMinutes = (pointer.y - grab.y - origin.y) / hourPx * 60f
        val snapped = snapToSlot(rawMinutes)
        val zone = dragZone ?: return
        val dayIndex = column(((pointer.x - origin.x) / columnPx).toInt().coerceIn(0, days.lastIndex))
        val target = days[dayIndex]
        val keep = dragKept
        // Measured where it would land: the drop keeps the real duration, which in
        // wall clock gains or loses the hour of a DST change it crosses (#387).
        val floor = dragFloorMin(
            clipOffsetMin,
            wallClockSpanMinutes(target, snapped - clipOffsetMin, dragDuration, zone, keep),
        )
        val held = snapped.coerceIn(floor, MINUTES_PER_DAY - DRAG_SNAP_MINUTES) - clipOffsetMin
        // A start in the spring-forward gap lands an hour on; show it there.
        val eventStartMin = wallClockInstant(target, held, zone, keep).minutesPast(target, zone)
        val startMin = (eventStartMin + clipOffsetMin).coerceAtMost(MINUTES_PER_DAY - DRAG_SNAP_MINUTES)
        val eventSpan = wallClockSpanMinutes(target, eventStartMin, dragDuration, zone, keep)
        val visible = -dayIndex..days.lastIndex - dayIndex
        val onGrid = dragSlices(eventStartMin, eventSpan, within = visible)
        val slices = onGrid.ifEmpty {
            listOfNotNull(edgeDragSlice(eventStartMin, eventSpan, visible))
        }
        drag = TimelineDrag(
            event = block.event,
            date = days[dayIndex],
            startMin = startMin,
            eventStartMin = eventStartMin,
            eventSpanMin = eventSpan,
            titleLines = titleLines,
            // Bounded to the columns this timeline actually shows: a day it
            // doesn't has no piece to draw. The write is unaffected.
            pieces = pieces(slices, dayIndex, origin),
            edgeOnly = onGrid.isEmpty(),
        )
    }

    /** [slices] as floating pieces, the first slice's day being [dayIndex] in the shown days. */
    private fun pieces(slices: List<DragSlice>, dayIndex: Int, origin: Offset): List<TimelineDragPiece> {
        val columnPx = geometry.columnWidthPx
        return slices.map { slice ->
            val bounds = dragPieceBounds(slice, geometry.hourPx, geometry.blockInsetPx)
            TimelineDragPiece(
                topLeftInRoot = Offset(
                    x = origin.x + column(dayIndex + slice.dayOffset) * columnPx,
                    y = origin.y + bounds.top,
                ),
                sizePx = IntSize(
                    (columnPx - geometry.columnGapPx).roundToInt(),
                    bounds.height.roundToInt(),
                ),
                continuesBefore = slice.continuesBefore,
                continuesAfter = slice.continuesAfter,
            )
        }
    }

    /** The on-screen column of a day index, and back: right-to-left lays the days out mirrored. */
    private fun column(index: Int): Int = if (geometry.isRtl) geometry.days.lastIndex - index else index

    /**
     * Hold the scroll for the whole drag and nudge it once per frame while the
     * finger sits near an edge. One `scroll` call, since a loop of
     * `animateScrollBy` would re-acquire the mutex and cancel the last.
     */
    suspend fun autoScroll() {
        val scroll = geometry.scroll ?: return
        scroll.scroll(MutatePriority.UserInput) {
            while (true) {
                withFrameNanos { }
                recompute()
                val step = edgeStep()
                if (step != 0f) scrollBy(step)
            }
        }
    }

    private fun edgeStep(): Float {
        val strip = stripDrag
        if (strip?.overGrid == false) return 0f
        val viewport = geometry.viewport?.takeIf { it.isAttached } ?: return 0f
        val edge = geometry.edgePx
        if (edge <= 0f) return 0f
        val bounds = viewport.boundsInRoot()
        val fromTop = pointer.y - bounds.top
        // Coming down out of the strip the finger starts inside the top edge.
        if (strip != null && !stripScrollArmed) {
            if (fromTop < edge) return 0f
            stripScrollArmed = true
        }
        val fromBottom = bounds.bottom - pointer.y
        return when {
            fromTop < edge -> -(edge - fromTop) / edge * geometry.stepPx
            fromBottom < edge -> (edge - fromBottom) / edge * geometry.stepPx
            else -> 0f
        }
    }
}

/** One resize gesture (#141): the edge it drags, and the edges as minutes from [day]'s midnight. */
private class ResizeSession(
    val edge: ResizeEdge,
    /** The day the resized event starts on. */
    val day: LocalDate,
    val startMin: Int,
    val endMin: Int,
) {
    /** How far the finger sits past the edge it grabbed, so the edge doesn't jump to it. */
    var grabMin = 0f

    /** The column the resized edge is in, held until the finger is well into another. */
    var column: Int? = null

    /** The slot the resized edge sits on, held against a finger trembling between two. */
    var slot: Int? = null

    /** The day, counted from the event's start day, the finger is resizing in. */
    var edgeDay = 0
}

@Composable
fun rememberTimelineDragController(): TimelineDragController {
    val controller = remember { TimelineDragController() }
    val density = LocalDensity.current
    controller.geometry.edgePx = with(density) { AUTO_SCROLL_EDGE.toPx() }
    controller.geometry.stepPx = with(density) { AUTO_SCROLL_STEP.toPx() }
    controller.slopPx = LocalViewConfiguration.current.touchSlop
    return controller
}

/**
 * How far this block's top edge sits past its event's start, in minutes — zero
 * for a block that begins on [day], and the part already run for the tail of an
 * event that started earlier, whose top edge is midnight rather than the event's
 * start. Dragging such a tail moves the event by where *its own* top edge lands,
 * so the offset has to come back off at the drop (#253).
 */
fun TimedBlock.clipOffsetMinutes(day: LocalDate, zone: TimeZone): Int {
    return (startMin - event.start.minutesPast(day, zone)).coerceAtLeast(0)
}

/**
 * True when the event began before [day] — this block is its tail, cut at
 * midnight rather than starting where the event does.
 */
fun TimedBlock.continuesBefore(day: LocalDate, zone: TimeZone): Boolean =
    event.start < day.atStartOfDayIn(zone)

/**
 * The instant a drop asks for — the *event's* new start, so the tail of an
 * overnight event drops back onto the day its own start belongs to. Read in the
 * zone the timeline is drawn in. A drop into a spring-forward gap has no instant
 * of its own; `toInstant` resolves it forward by the missing hour, and the block
 * visibly settles there.
 */
fun TimelineDrop.startInstant(zone: TimeZone): Instant =
    dropStartInstant(event, date, startMin, clipOffsetMin, zone)

/** Where [event] starts when its block's top edge lands [startMin] past [date]'s midnight — see [TimelineDrop]. */
internal fun dropStartInstant(
    event: EventInstance,
    date: LocalDate,
    startMin: Int,
    clipOffsetMin: Int,
    zone: TimeZone,
): Instant = wallClockInstant(date, startMin - clipOffsetMin, zone, kept = zone.repeatedHourOffsetAt(event.start))

/**
 * The wall-clock minutes an event lasting [duration] covers when it starts
 * [startMin] past [date]'s midnight in [zone] — its real length, give or take a
 * DST change in between (#387).
 */
internal fun wallClockSpanMinutes(
    date: LocalDate,
    startMin: Int,
    duration: Duration,
    zone: TimeZone,
    kept: UtcOffset? = null,
): Int {
    val start = wallClockInstant(date, startMin, zone, kept)
    return wallClockEndMinutes(startMin, start, start + duration, date, zone) - startMin
}

/**
 * [minutes] past [date]'s midnight, either way past it, as wall clock in [zone]
 * (DST-safe). A time the fall-back hour repeats lands on its earlier pass, unless
 * [kept] is its later one: an event there stays on it (#387). Pass
 * [repeatedHourOffsetAt] of the event's time, which is null outside the hour —
 * such an instant says nothing about which pass the grid slot means, so a drop
 * never depends on the season the event came from.
 */
fun wallClockInstant(date: LocalDate, minutes: Int, zone: TimeZone, kept: UtcOffset? = null): Instant =
    wallClockTime(date, minutes).resolveIn(zone, kept)

/** The wall-clock time [minutes] past [date]'s midnight, on a later day past 1440. */
fun wallClockTime(date: LocalDate, minutes: Int): LocalDateTime {
    val day = date.plus(minutes.floorDiv(MINUTES_PER_DAY), DateTimeUnit.DAY)
    val minute = minutes.mod(MINUTES_PER_DAY)
    return LocalDateTime(day, LocalTime(minute / 60, minute % 60))
}

/** An event's span as wall-clock minutes from its start day's midnight. */
data class WallClockSpan(val day: LocalDate, val startMin: Int, val endMin: Int)

/**
 * [event]'s [WallClockSpan], its end past 1440 when it ends on a later day
 * (DST-safe). One ending in the fall-back hour's repeat ends where that hour does.
 */
fun wallClockSpan(event: EventInstance, zone: TimeZone): WallClockSpan {
    val start = event.start.toLocalDateTime(zone)
    val startMin = start.time.toSecondOfDay() / 60
    return WallClockSpan(
        start.date,
        startMin,
        wallClockEndMinutes(startMin, event.start, event.end, start.date, zone),
    )
}

/**
 * A beat after the grid draws the drop, so its own block is under way before the
 * copy starts dissolving.
 */
const val SETTLE_GRACE_MILLIS: Long = 60L

/**
 * How long to wait for a grid that never confirms the drop — it landed on a day
 * this timeline doesn't show, or the write failed. The copy has to go either way.
 */
private const val SETTLE_TIMEOUT_MILLIS = 900L

/**
 * The hand-over: the copy dissolves over this while the grid's own block slides
 * in under it. Kept short, because a full-width copy sits over any neighbour it
 * now shares a lane with until it is gone.
 */
const val SETTLE_FADE_MILLIS: Int = 250


/**
 * The floating block, drawn over the whole calendar so it is free of the day
 * column's clip and of the scroll viewport's rounded corners.
 */
@Composable
fun TimelineDragOverlay(controller: TimelineDragController, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val reduceMotion = rememberReduceMotion()
    val drops = dropStates()
    val moveInFlight = controller.settledToken in drops.pending
    val stripInFlight = controller.stripToken in drops.pending

    // Both live here rather than beside the controller: they read [drag], which
    // changes every frame, and this composable is the one that is meant to.
    DragSnapHaptics(controller.drag?.slot ?: controller.stripDrag?.dayShift, controller.drops)
    // Only once the finger has moved: a long press held near an edge must not
    // scroll the drop target out from under a finger that only means to select.
    LaunchedEffect(controller.isDragging && controller.pointerMoved) {
        if (controller.isDragging && controller.pointerMoved) controller.autoScroll()
    }
    // Hold the landed copy until the write is done — including the time a
    // recurring drop's scope dialog is up — and then until the grid draws the
    // drop itself.
    var handingOver by remember(controller.settling) { mutableStateOf(false) }
    val abandoned = controller.settledToken in drops.abandoned
    LaunchedEffect(controller.settling, moveInFlight, controller.settledOnGrid, abandoned) {
        if (controller.settling == null || moveInFlight) return@LaunchedEffect
        // An abandoned drop: nothing will land and there is nothing to wait for.
        if (!abandoned) {
            delay(if (controller.settledOnGrid) SETTLE_GRACE_MILLIS else SETTLE_TIMEOUT_MILLIS)
        }
        handingOver = true
        controller.handOver()
        delay(SETTLE_FADE_MILLIS.toLong())
        controller.release()
    }

    // The same hold for a bar moved along the all-day strip.
    val stripAbandoned = controller.stripToken in drops.abandoned
    LaunchedEffect(controller.stripSettling, stripInFlight, controller.stripSeated, stripAbandoned) {
        if (controller.stripSettling == null || stripInFlight) return@LaunchedEffect
        if (!stripAbandoned) {
            delay(if (controller.stripSeated) SETTLE_GRACE_MILLIS else SETTLE_TIMEOUT_MILLIS)
        }
        controller.releaseStrip()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // A copy of a block still in the tree behind it; announcing it again
            // would duplicate the event for the drag's duration.
            .clearAndSetSemantics { }
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        controller.held.forEach { parked ->
            key(parked.token) { HeldTimelineCopy(controller, parked, origin) }
        }
        controller.heldStrips.forEach { parked ->
            key(parked.token) { HeldStripSettle(controller, parked) }
        }
        val drag = controller.drag ?: controller.settling ?: return@Box
        // Landed: the copy sinks back to the grid's own plane while the write runs.
        val landed = controller.drag == null
        val lift by animateFloatAsState(
            // A selected event's block is lifted too: sinking first made it shrink, then grow back.
            targetValue = if (reduceMotion || (landed && !controller.settlingSelected)) 0f else 1f,
            label = "drag-lift",
        )
        val copyAlpha by animateFloatAsState(
            targetValue = if (handingOver) 0f else 1f,
            animationSpec = tween(SETTLE_FADE_MILLIS),
            label = "drag-handover",
        )
        TimelineCopy(drag, controller.geometry, origin, lift, copyAlpha)
    }
}

/**
 * [TimelineDragController.ghosts] for [block], derived so the block recomposes
 * only when its own answer changes rather than on every lift, park and release.
 */
@Composable
fun TimelineDragController.ghostsDerived(block: TimedBlock): Boolean {
    val ghosted by remember(this, block) { derivedStateOf { ghosts(block) } }
    return ghosted
}

/** A drop parked by a newer pickup: held at its landing slot until its own write settles. */
@Composable
private fun HeldTimelineCopy(controller: TimelineDragController, parked: HeldDrop, origin: Offset) {
    val alpha = rememberHeldSettle(
        token = parked.token,
        seated = parked.seated,
        timeoutMillis = SETTLE_TIMEOUT_MILLIS,
        onHandOver = { controller.handOverHeld(parked.token) },
        onRelease = { controller.releaseHeld(parked.token) },
    )
    TimelineCopy(parked.copy, controller.geometry, origin, lift = 0f, copyAlpha = alpha)
}

/** A strip drop parked by a newer one: shown at its new days until its own write settles. */
@Composable
private fun HeldStripSettle(controller: TimelineDragController, parked: HeldStrip) {
    rememberHeldSettle(
        token = parked.token,
        seated = parked.seated,
        timeoutMillis = SETTLE_TIMEOUT_MILLIS,
        onHandOver = {},
        onRelease = { controller.releaseHeldStrip(parked.token) },
    )
}

/** [drag]'s pieces drawn over the calendar, clipped to the timeline's viewport. */
@Composable
private fun TimelineCopy(
    drag: TimelineDrag,
    geometry: TimelineGeometry,
    origin: Offset,
    lift: Float,
    copyAlpha: Float,
) {
    val density = LocalDensity.current
    val dark = LocalFloretDarkTheme.current
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
        val paint = eventPaint(drag.event, dark)
        val title = drag.event.title.ifBlank { stringResource(R.string.event_untitled) }
        // Read off the event rather than the held block, so grabbing either half
        // of an event that crosses midnight names the same hours. An end past
        // midnight wraps rather than saturating, so a four-hour event dragged to
        // 22:00 reads "22:00–02:00" and not "22:00–24:00".
        val startMin = drag.eventStartMin.mod(MINUTES_PER_DAY)
        val rawEnd = startMin + drag.eventSpanMin
        val endMin = if (rawEnd > MINUTES_PER_DAY) rawEnd % MINUTES_PER_DAY else rawEnd
        val range = formatMinuteOfDay(startMin, use24Hour, locale) to
            formatMinuteOfDay(endMin, use24Hour, locale)
        // The timeline's own bounds. The copy is drawn over the whole calendar
        // so no column clip or rounded corner cuts it, but it still belongs to
        // the timeline: a piece a whole day tall reaches far past both ends of
        // the viewport, and unclipped it paints over the headers above (#267).
        val port = geometry.viewport?.takeIf { it.isAttached }?.boundsInRoot()
        // How much of each piece the viewport actually shows. A multi-day event
        // has a piece per day, and the widest one is a full 24 hours — taller
        // than the screen, its top at a midnight scrolled out of sight. Picking
        // the range's piece by raw height put it there, off the top of the
        // timeline; picking by *visible* height puts it where it can be read.
        val shown = drag.pieces.map { piece ->
            if (port == null) {
                piece.sizePx.height.toFloat()
            } else {
                val top = maxOf(piece.topLeftInRoot.y, port.top)
                val bottom = minOf(piece.topLeftInRoot.y + piece.sizePx.height, port.bottom)
                bottom - top
            }
        }
        val labelled = shown.indices.maxByOrNull { shown[it] }
        Box(
            modifier = if (port == null) {
                Modifier
            } else {
                Modifier
                    .absoluteOffset {
                        IntOffset(
                            (port.left - origin.x).roundToInt(),
                            (port.top - origin.y).roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { port.width.toDp() },
                        height = with(density) { port.height.toDp() },
                    )
                    .clipToBounds()
            },
        ) {
            val pieceOrigin = port?.topLeft ?: origin
            drag.pieces.forEachIndexed { index, piece ->
                DragCopy(
                    topLeftInRoot = piece.topLeftInRoot,
                    overlayOrigin = pieceOrigin,
                    sizePx = piece.sizePx,
                    paint = paint,
                    shape = timedBlockShape(piece.continuesBefore, piece.continuesAfter),
                    cuts = timedBlockCuts(piece.continuesBefore, piece.continuesAfter),
                    lift = lift,
                    alpha = copyAlpha,
                    title = title,
                    titleLines = drag.titleLines,
                    // Once for the whole event: repeated, it would name it per day.
                    range = range.takeIf { index == labelled },
                )
            }
        }
}

/** One piece of a dragged block, floating over the calendar. */
@Composable
private fun DragCopy(
    topLeftInRoot: Offset,
    overlayOrigin: Offset,
    sizePx: IntSize,
    paint: EventPaint,
    shape: RoundedCornerShape,
    cuts: ChipCuts,
    lift: Float,
    alpha: Float,
    title: String,
    titleLines: Int,
    range: Pair<String, String>?,
) {
    val density = LocalDensity.current
    val width = with(density) { sizePx.width.toDp() }
    val height = with(density) { sizePx.height.toDp() }
    val textWidth = width - (BLOCK_OUTER_INSET + BLOCK_TEXT_PADDING) * 2
    // The block's size, spent the block's way — text sits at the top as it does
    // on the block, so the copy hands back to the grid without shifting (#267),
    // and it squeezes its inset on the same terms so a short block's title does
    // not vanish the moment it is lifted (#289).
    // The title is served in full first and the range lives off what is left:
    // the hour gutter down the side still says where the copy sits, so the
    // range is the half that can afford to go.
    val metrics = rememberBlockTextMetrics(height)
    val allowed = titleLines.coerceAtMost(metrics.titleBudget(metrics.available))
    // Re-measured at the copy's own width rather than spent on the source's
    // count: a block sharing its column with another is a lane wide where the
    // copy is a whole column, so the source's second line is one the copy never
    // draws — and reserving it took the range away with it (#267).
    val lines = if (allowed <= 0) {
        0
    } else {
        blockTextLines(
            text = title,
            style = MaterialTheme.typography.labelMedium.withTitleWeight(paint),
            textWidth = textWidth,
            max = allowed,
        )
    }
    val left = metrics.available - metrics.titleHeight(lines)
    val label = range?.let { rememberBlockTimeRange(it.first, it.second, textWidth) }
    val showTime = label != null && left >= metrics.timeLine
    val timeMaxLines = if (showTime) {
        blockTimeLines(label!!, textWidth, left - metrics.timeLine)
    } else {
        1
    }
    Box(
        modifier = Modifier
            // Absolute: these are root coordinates, and the direction-aware
            // offset would mirror them across the screen in an RTL layout.
            .absoluteOffset {
                IntOffset(
                    (topLeftInRoot.x - overlayOrigin.x).roundToInt(),
                    (topLeftInRoot.y - overlayOrigin.y).roundToInt(),
                )
            }
            // Required: the parent is the viewport's size, and a plain `size`
            // would let it cap a piece a whole day tall at one screen — drawn
            // from a midnight scrolled off the top, that ended the column a
            // scroll offset short of its own bottom (#267). The parent clips it.
            .requiredSize(width = width, height = height)
            .padding(horizontal = BLOCK_OUTER_INSET)
            .graphicsLayer {
                // Anchored at the top edge, not the middle: scaled about the
                // centre, the 2% lift raises the top by 1% of the height — a few
                // pixels on an hour, but a steady upward creep of the text as a
                // dragged multi-day piece grows, and a shear against the
                // timeline's clip. It snapped back when the lift animated out
                // on drop (#267).
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = 1f + 0.02f * lift
                scaleY = 1f + 0.02f * lift
                shadowElevation = 8.dp.toPx() * lift
                this.alpha = alpha
                this.shape = shape
                clip = false
            }
            .eventSurface(paint, shape, cuts)
            .padding(horizontal = BLOCK_TEXT_PADDING, vertical = metrics.inset),
    ) {
        Column {
            if (lines > 0) {
                BlockTitle(
                    title = title,
                    maxLines = lines,
                    color = paint.titleInk,
                    textDecoration = paint.decoration,
                    fontWeight = paint.titleWeight,
                )
            }
            if (showTime) {
                val overflow = eventTitleOverflow(singleLine = timeMaxLines == 1)
                Text(
                    text = label,
                    // As the block it lifted off sets its own time (#219).
                    style = rememberPackedLines(MaterialTheme.typography.labelSmall.asEventTime()),
                    maxLines = timeMaxLines,
                    overflow = overflow.overflow,
                    softWrap = overflow.softWrap,
                    color = paint.secondaryInk,
                )
            }
        }
    }
}

/** How far a selected block is lifted, 0 to 1 (#141); [snap] takes it over at once. */
@Composable
fun selectionLift(selected: Boolean, snap: Boolean = false): Float {
    val reduceMotion = rememberReduceMotion()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val raise = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (reduceMotion || (selected && snap)) raise.snapTo(target) else raise.animateTo(target, spec)
    }
    // The effect runs a frame late, and that frame would show the block unlifted.
    return if (selected && snap) 1f else raise.value
}

/**
 * A selected block raised off the grid, the way a dragged copy is: scaled from
 * its top edge so the text doesn't creep, with a shadow in the block's shape.
 */
fun Modifier.selectionLift(raise: Float, shape: Shape): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0.5f, 0f)
    scaleX = 1f + 0.02f * raise
    scaleY = 1f + 0.02f * raise
    shadowElevation = 6.dp.toPx() * raise
    this.shape = shape
    clip = false
}
