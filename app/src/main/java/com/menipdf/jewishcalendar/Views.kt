package com.menipdf.jewishcalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZonedDateTime

private val SAT_TINT_LIGHT = Color(0xFFFAFBFD)
private val YT_TINT_LIGHT = Color(0xFFF1F8F4)

@Composable
private fun cellBg(sat: Boolean, chag: Boolean): Color {
    val dark = MaterialTheme.colorScheme.background.red < 0.5f
    return when {
        chag -> if (dark) Color(0xFF1F2B24) else YT_TINT_LIGHT
        sat -> if (dark) Color(0xFF242528) else SAT_TINT_LIGHT
        else -> MaterialTheme.colorScheme.background
    }
}

/* ======================= תצוגת חודש ======================= */
@Composable
fun MonthView(vm: AppViewModel, engine: Engine, onEvent: (Ev) -> Unit) {
    val c = vm.cursor
    val first = c.withDayOfMonth(1)
    val startOffset = first.wd()
    val weeks = (startOffset + c.lengthOfMonth() + 6) / 7
    val start = first.minusDays(startOffset.toLong())
    val enabled = vm.filters.toMap()
    val line = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            WD_LETTER.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium
                )
            }
        }
        HorizontalDivider(color = line)
        for (w in 0 until weeks) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (i in 0..6) {
                    val d = start.plusDays((w * 7 + i).toLong())
                    DayCell(d, d.month == c.month, engine, enabled, Modifier.weight(1f).fillMaxHeight()) {
                        vm.goDay(d)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    engine: Engine,
    enabled: Map<Cat, Boolean>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val (allDay, timed) = remember(engine, date, enabled) { engine.items(date) { enabled[it] ?: true } }
    val items = allDay + timed
    val chag = remember(engine, date) { engine.isChag(date) }
    val line = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(
        modifier
            .background(cellBg(date.wd() == 6, chag))
            .border(0.5.dp, line)
            .clickable(onClick = onClick)
    ) {
        val fit = ((maxHeight - 24.dp) / 16.dp).toInt()
        Column(Modifier.fillMaxSize().padding(1.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isToday = date == today
                Box(
                    Modifier
                        .size(20.dp)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${date.dayOfMonth}", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            inMonth -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                val hd = engine.hebDay(date)
                val first = engine.hebDayOfMonth(date) == 1
                Text(
                    if (first) "$hd ${engine.hebMonth(date)}" else hd,
                    fontSize = if (first) 9.sp else 10.sp,
                    fontWeight = if (first) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Clip,
                    color = if (first) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val show = if (items.size > fit) items.take((fit - 1).coerceAtLeast(0)) else items
            show.forEach { Chip(it, engine) }
            if (show.size < items.size) {
                Text(
                    "עוד ${items.size - show.size}", fontSize = 9.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun Chip(e: Ev, engine: Engine) {
    if (e.timeMillis != null) {
        Row(
            Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(e.cat.color, CircleShape))
            Spacer(Modifier.width(3.dp))
            Text(engine.hm(e.timeMillis), fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
        }
    } else {
        Text(
            e.label, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = Color.White, fontWeight = if (e.chag) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.5.dp)
                .height(15.dp)
                .background(e.cat.color, RoundedCornerShape(3.dp))
                .padding(horizontal = 3.dp)
        )
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
    val hourH = 52.dp
    val gutter = 40.dp
    val line = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    LaunchedEffect(days.first()) {
        val nowMin = ZonedDateTime.now(engine.zone).let { it.hour * 60 + it.minute }
        val target = if (days.contains(today)) (nowMin - 120).coerceAtLeast(0) else 5 * 60
        vScroll.scrollTo(with(density) { (hourH * (target / 60f)).roundToPx() })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colW: Dp = maxOf(84.dp, (maxWidth - gutter) / 7)
        Column(Modifier.horizontalScroll(hScroll).width(gutter + colW * 7).fillMaxHeight()) {
            // כותרות ימים
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(gutter))
                days.forEach { d ->
                    Column(
                        Modifier.width(colW).clickable { vm.goDay(d) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isToday = d == today
                        Text(
                            "יום ${WD_LETTER[d.wd()]}", fontSize = 11.sp,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            Modifier.size(34.dp)
                                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${d.dayOfMonth}", fontSize = 18.sp,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            "${engine.hebDay(d)} ${engine.hebMonth(d)}", fontSize = 10.sp, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // כל היום
            Row(Modifier.fillMaxWidth().border(0.5.dp, line)) {
                Box(Modifier.width(gutter), contentAlignment = Alignment.TopCenter) {
                    Text("כל היום", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                days.forEach { d ->
                    val (allDay, _) = engine.items(d) { enabled[it] ?: true }
                    Column(Modifier.width(colW).padding(2.dp).defaultMinSize28()) {
                        allDay.forEach { e ->
                            Text(
                                e.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = Color.White, fontWeight = if (e.chag) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                                    .background(e.cat.color, RoundedCornerShape(4.dp))
                                    .clickable { onEvent(e) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            // רשת שעות
            Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
                Box(Modifier.width(gutter).height(hourH * 24)) {
                    for (h in 1..23) {
                        Text(
                            "%02d:00".format(h), fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = 4.dp, y = hourH * h - 7.dp)
                        )
                    }
                }
                days.forEach { d ->
                    DayColumn(d, engine, enabled, colW, hourH, false, onEvent)
                }
            }
        }
    }
}

private fun Modifier.defaultMinSize28(): Modifier = this.height(28.dp)

@Composable
private fun DayColumn(
    d: LocalDate,
    engine: Engine,
    enabled: Map<Cat, Boolean>,
    colW: Dp,
    hourH: Dp,
    full: Boolean,
    onEvent: (Ev) -> Unit
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val slots = remember(engine, d, enabled, full) {
        val (_, timed) = engine.items(d) { enabled[it] ?: true }
        val list = ArrayList<Slot>()
        timed.forEach { e -> list += Slot(engine.minutesOfDay(e.timeMillis ?: 0L), 0, e) }
        val taken = list.map { it.start }
        if (enabled[Cat.ZMANIM] != false) {
            engine.zmanim(d).filter { full || it.key }.forEach { z ->
                val st = engine.minutesOfDay(z.timeMillis)
                if (z.id == "tzeit" && taken.any { kotlin.math.abs(it - st) <= 1 }) return@forEach
                list += Slot(st, 0, zmanToEv(z, d, engine, !full))
            }
        }
        val fixed = list.map { Slot(it.start, it.start + 25, it.ev) }.toMutableList()
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
            Row(
                Modifier
                    .offset(x = w * s.col, y = hourH * (s.start / 60f))
                    .width(w - 1.dp)
                    .height(22.dp)
                    .background(
                        if (isZ) e.cat.color.copy(alpha = 0.18f) else e.cat.color,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onEvent(e) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tc = if (isZ) MaterialTheme.colorScheme.onSurface else Color.White
                Text(e.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tc, modifier = Modifier.weight(1f))
                Text(engine.hm(e.timeMillis ?: 0L), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tc, maxLines = 1)
            }
        }
        if (isToday) {
            val now = ZonedDateTime.now(engine.zone)
            val m = now.hour * 60 + now.minute
            Box(
                Modifier.offset(y = hourH * (m / 60f)).fillMaxWidth().height(2.dp).background(Color(0xFFEA4335))
            )
        }
    }
}

/* ======================= תצוגת יום ======================= */
@Composable
fun DayView(vm: AppViewModel, engine: Engine, onEvent: (Ev) -> Unit) {
    val d = vm.cursor
    val enabled = vm.filters.toMap()
    val events = remember(engine, d) { engine.events(d).filter { it.cat != Cat.DAF || enabled[Cat.DAF] == true } }
    val zm = remember(engine, d) { engine.zmanim(d) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(engine.hebFull(d), fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        Text("${d.weekdayName()}, ${d.longDate()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (events.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("אירועים", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            events.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEvent(e) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).background(e.cat.color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(e.full, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("זמני היום · ${engine.city.name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        zm.forEach { z ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { onEvent(zmanToEv(z, d, engine, false)) }.padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween
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
                Column(Modifier.width(78.dp).clickable { vm.goDay(d) }) {
                    Box(
                        Modifier.size(34.dp)
                            .background(if (d == today) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${d.dayOfMonth}", fontSize = 18.sp,
                            color = if (d == today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "${GREG_MONTHS[d.monthValue - 1]}, ${WD_LETTER[d.wd()]}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${engine.hebDay(d)} ${engine.hebMonth(d)}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f)) {
                    evs.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onEvent(e) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(12.dp).background(e.cat.color, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                e.timeMillis?.let { engine.hm(it) } ?: "כל היום",
                                modifier = Modifier.width(52.dp), fontSize = 13.sp,
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
