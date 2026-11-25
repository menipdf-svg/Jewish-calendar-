package com.menipdf.jewishcalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZonedDateTime

/** רקע עדין לשבת ולחג – ללא צבעים דקורטיביים */
@Composable
private fun cellBg(sat: Boolean, chag: Boolean): Color {
    val dark = isDark()
    return when {
        chag -> if (dark) Color(0xFF1F2B24) else Color(0xFFF1F8F4)
        sat -> if (dark) Color(0xFF26272A) else Color(0xFFF8F9FB)
        else -> MaterialTheme.colorScheme.background
    }
}

/* ======================= תצוגת חודש ======================= */

private const val MAX_CHIPS = 3

/**
 * גובה כל שורת שבוע נקבע לפי התוכן הגבוה ביותר בה (IntrinsicSize),
 * עם גובה מינימלי שממלא את המסך. הלוח כולו נגלל כשצריך.
 */
@Composable
fun MonthView(vm: AppViewModel, engine: Engine, onDay: (LocalDate) -> Unit) {
    val c = vm.cursor
    val first = c.withDayOfMonth(1)
    val startOffset = first.wd()
    val weeks = (startOffset + c.lengthOfMonth() + 6) / 7
    val start = first.minusDays(startOffset.toLong())
    val enabled = vm.filters.toMap()
    val today = LocalDate.now()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minRow = maxOf(92.dp, (maxHeight - 32.dp) / weeks)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            WeekdayHeader()
            for (w in 0 until weeks) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = minRow)
                        .height(IntrinsicSize.Min)
                ) {
                    for (i in 0..6) {
                        val d = start.plusDays((w * 7 + i).toLong())
                        DayCell(
                            date = d, inMonth = d.month == c.month, today = today,
                            engine = engine, enabled = enabled,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onDay(d) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        WD_LETTER.forEachIndexed { i, l ->
            Text(
                l, Modifier.weight(1f), textAlign = TextAlign.Center, style = JcText.weekday,
                color = if (i == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shortTimeLabel(e: Ev): String = if (e.key == "candle") "כניסה" else "יציאה"

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    today: LocalDate,
    engine: Engine,
    enabled: Map<Cat, Boolean>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val (allDay, timed) = remember(engine, date, enabled) { engine.items(date) { enabled[it] ?: true } }
    val chag = remember(engine, date) { engine.isChag(date) }
    val isToday = date == today
    val dark = isDark()
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val hebDay = remember(engine, date) { engine.hebDay(date) }
    val hebFirst = remember(engine, date) { engine.hebDayOfMonth(date) == 1 }
    val hebText = if (hebFirst) "$hebDay ${engine.hebMonth(date)}" else hebDay

    // מעל MAX_CHIPS אירועים: מציגים MAX_CHIPS-1 ואת השאר מסכמים ב"+N"
    val overflow = allDay.size > MAX_CHIPS
    val shown = if (overflow) allDay.take(MAX_CHIPS - 1) else allDay
    val hidden = allDay.size - shown.size

    val a11y = buildString {
        append("${date.weekdayName()}, ${date.longDate()}, ${engine.hebFull(date)}")
        allDay.forEach { append(", ${it.full}") }
        timed.forEach { append(", ${it.full}") }
    }

    val bg = cellBg(date.wd() == 6, chag)
    Column(
        modifier
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.5f else 0.45f) else bg)
            .drawBehind {
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }
            .semantics(mergeDescendants = true) {
                contentDescription = a11y
                role = Role.Button
            }
            .clickable(onClickLabel = "פרטי היום", onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        // 1. תאריך לועזי (ראשי) + 2. תאריך עברי (משני)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${date.dayOfMonth}", style = JcText.gregDay,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        inMonth -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
            Text(
                hebText,
                style = JcText.hebDay.copy(fontWeight = if (hebFirst) FontWeight.Bold else FontWeight.Medium),
                color = if (hebFirst) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp)
            )
        }
        // 3-4. חגים, אירועים ופרשה
        shown.forEach { EventChip(it, Modifier.padding(top = 3.dp), maxLines = 2) }
        if (hidden > 0) {
            Text(
                "+$hidden עוד", style = JcText.caption, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp), maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        // 5. כניסת / יציאת שבת – תמיד בתחתית התא, עם תווית מילולית
        timed.forEach { t ->
            Column(Modifier.padding(top = 4.dp, start = 2.dp)) {
                Text(
                    shortTimeLabel(t), style = JcText.caption,
                    color = tonalText(Cat.SHABBAT, dark), maxLines = 1
                )
                Text(
                    engine.hm(t.timeMillis ?: 0L), style = JcText.timeValue,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                )
            }
        }
    }
}

/* ======================= תצוגת שבוע ======================= */
private class Slot(val start: Int, val end: Int, val ev: Ev, var col: Int = 0, var cols: Int = 1)

private fun layoutSlots(items: MutableList<Slot>) {
    items.sortBy { it.start }
    var cluster = ArrayList<Slot>()
    var clusterEnd = -1
    fun flush() {
        val cols = ArrayList<Int>()
        for (s in cluster) {
            var i = cols.indexOfFirst { end -> end <= s.start }
            if (i < 0) {
                cols.add(s.end)
                i = cols.size - 1
            } else {
                cols[i] = s.end
            }
            s.col = i
        }
        cluster.forEach { it.cols = cols.size }
        cluster = ArrayList()
    }
    for (s in items) {
        if (cluster.isNotEmpty() && s.start >= clusterEnd) {
            flush()
            clusterEnd = -1
        }
        cluster.add(s)
        clusterEnd = maxOf(clusterEnd, s.end)
    }
    if (cluster.isNotEmpty()) flush()
}

private fun zmanToEv(z: Zman, d: LocalDate, engine: Engine, short: Boolean) = Ev(
    d, Cat.ZMANIM, if (short) z.short else z.label, "${z.label}: ${engine.hm(z.timeMillis)}",
    z.timeMillis, memo = z.desc
)

@Composable
fun WeekView(vm: AppViewModel, engine: Engine, onEvent: (Ev) -> Unit) {
    val days = remember(vm.cursor) { (0..6).map { vm.cursor.startOfWeek().plusDays(it.toLong()) } }
    val enabled = vm.filters.toMap()
    val today = LocalDate.now()
    val hourH = 56.dp
    val gutter = 44.dp
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    LaunchedEffect(days.first()) {
        val nowMin = ZonedDateTime.now(engine.zone).let { it.hour * 60 + it.minute }
        val target = if (days.contains(today)) (nowMin - 120).coerceAtLeast(0) else 5 * 60
        vScroll.scrollTo(with(density) { (hourH * (target / 60f)).roundToPx() })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colW: Dp = maxOf(92.dp, (maxWidth - gutter) / 7)
        Column(Modifier.horizontalScroll(hScroll).width(gutter + colW * 7).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(gutter))
                days.forEach { d ->
                    Column(
                        Modifier.width(colW).clickable { vm.goDay(d) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isToday = d == today
                        Text(
                            "יום ${WD_LETTER[d.wd()]}", style = JcText.weekday,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            Modifier.size(36.dp)
                                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${d.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            "${engine.hebDay(d)} ${engine.hebMonth(d)}", style = JcText.caption, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = line)
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.width(gutter).padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
                    Text("כל היום", style = JcText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                days.forEach { d ->
                    val (allDay, _) = engine.items(d) { enabled[it] ?: true }
                    Column(Modifier.width(colW).heightIn(min = 32.dp).padding(2.dp)) {
                        allDay.forEach { e ->
                            Box(Modifier.padding(bottom = 2.dp).clickable { onEvent(e) }) { EventChip(e, maxLines = 2) }
                        }
                    }
                }
            }
            HorizontalDivider(color = line)
            Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
                Box(Modifier.width(gutter).height(hourH * 24)) {
                    for (h in 1..23) {
                        Text(
                            "%02d:00".format(h), style = JcText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = 4.dp, y = hourH * h - 8.dp)
                        )
                    }
                }
                days.forEach { d -> DayColumn(d, engine, enabled, colW, hourH, onEvent) }
            }
        }
    }
}

@Composable
private fun DayColumn(
    d: LocalDate,
    engine: Engine,
    enabled: Map<Cat, Boolean>,
    colW: Dp,
    hourH: Dp,
    onEvent: (Ev) -> Unit
) {
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val slots = remember(engine, d, enabled) {
        val (_, timed) = engine.items(d) { enabled[it] ?: true }
        val list = ArrayList<Slot>()
        timed.forEach { e -> list += Slot(engine.minutesOfDay(e.timeMillis ?: 0L), 0, e) }
        val taken = list.map { it.start }
        if (enabled[Cat.ZMANIM] != false) {
            engine.zmanim(d).filter { it.key }.forEach { z ->
                val st = engine.minutesOfDay(z.timeMillis)
                if (z.id == "tzeit" && taken.any { kotlin.math.abs(it - st) <= 1 }) return@forEach
                list += Slot(st, 0, zmanToEv(z, d, engine, true))
            }
        }
        val fixed = list.map { Slot(it.start, it.start + 28, it.ev) }.toMutableList()
        layoutSlots(fixed)
        fixed
    }
    val isToday = d == LocalDate.now()
    Box(
        Modifier
            .width(colW)
            .height(hourH * 24)
            .background(if (d.wd() == 6) cellBg(true, false) else Color.Transparent)
            .drawBehind {
                val hp = hourH.toPx()
                for (h in 0..24) drawLine(line, Offset(0f, h * hp), Offset(size.width, h * hp), 1f)
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }
    ) {
        slots.forEach { s ->
            val w = colW / s.cols
            val e = s.ev
            val isZ = e.cat == Cat.ZMANIM
            val fg = if (isZ) MaterialTheme.colorScheme.onSurface else Color.White
            Row(
                Modifier
                    .offset(x = w * s.col, y = hourH * (s.start / 60f))
                    .width(w - 1.dp)
                    .height(26.dp)
                    .background(
                        if (isZ) e.cat.color.copy(alpha = 0.2f) else e.cat.color,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onEvent(e) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(e.label, style = JcText.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg, modifier = Modifier.weight(1f))
                Text(engine.hm(e.timeMillis ?: 0L), style = JcText.caption.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
            }
        }
        if (isToday) {
            val now = ZonedDateTime.now(engine.zone)
            val m = now.hour * 60 + now.minute
            Box(Modifier.offset(y = hourH * (m / 60f)).fillMaxWidth().height(2.dp).background(Color(0xFFEA4335)))
        }
    }
}

/* ======================= תצוגת יום ======================= */
@Composable
fun DayView(vm: AppViewModel, engine: Engine, onEvent: (Ev) -> Unit) {
    val d = vm.cursor
    val enabled = vm.filters.toMap()
    val events = remember(engine, d, enabled) { engine.events(d).filter { enabled[it.cat] ?: true } }
    val zm = remember(engine, d) { engine.zmanim(d) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(engine.hebFull(d), fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        Text("${d.weekdayName()}, ${d.longDate()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (events.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("אירועים", style = JcText.chip, color = MaterialTheme.colorScheme.onSurfaceVariant)
            events.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onEvent(e) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).background(e.cat.color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(e.full, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("זמני היום · ${engine.city.name}", style = JcText.chip, color = MaterialTheme.colorScheme.onSurfaceVariant)
        zm.forEach { z ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onEvent(zmanToEv(z, d, engine, false)) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(z.label)
                Text(engine.hm(z.timeMillis), fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ======================= לוח זמנים ======================= */
@Composable
fun AgendaView(vm: AppViewModel, engine: Engine, onEvent: (Ev) -> Unit) {
    val enabled = vm.filters.toMap()
    val today = LocalDate.now()
    val days = remember(engine, vm.cursor, enabled) {
        (0..29).map { vm.cursor.plusDays(it.toLong()) }.mapNotNull { d ->
            val (a, t) = engine.items(d) { enabled[it] ?: true }
            if (a.isEmpty() && t.isEmpty()) null else d to (a + t)
        }
    }
    if (days.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("אין אירועים בטווח הזה עם המסננים הנוכחיים.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(days) { (d, evs) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Column(Modifier.width(84.dp).clickable { vm.goDay(d) }) {
                    Box(
                        Modifier.size(36.dp)
                            .background(if (d == today) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${d.dayOfMonth}", fontSize = 18.sp,
                            color = if (d == today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "${GREG_MONTHS[d.monthValue - 1]}, ${WD_LETTER[d.wd()]}", style = JcText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${engine.hebDay(d)} ${engine.hebMonth(d)}", style = JcText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f)) {
                    evs.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onEvent(e) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(12.dp).background(e.cat.color, RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                e.timeMillis?.let { engine.hm(it) } ?: "כל היום",
                                modifier = Modifier.width(56.dp), style = JcText.chip,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(e.label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
