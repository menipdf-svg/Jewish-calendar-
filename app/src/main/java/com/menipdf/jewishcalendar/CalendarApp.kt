package com.menipdf.jewishcalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarApp(vm: AppViewModel = viewModel()) {
    JcTheme(isDarkTheme(vm.theme)) {
        val engine = remember(vm.city, vm.candleMinutes, vm.havdalah) {
            Engine(vm.city, vm.candleMinutes, vm.havdalah)
        }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var showSettings by remember { mutableStateOf(false) }
        var showPicker by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Ev?>(null) }
        var dayDetail by remember { mutableStateOf<LocalDate?>(null) }

        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(vm, engine) {
                        scope.launch { drawer.close() }
                        showSettings = true
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CalendarTopBar(
                        vm = vm, engine = engine,
                        onMenu = { scope.launch { drawer.open() } },
                        onSettings = { showSettings = true },
                        onPickMonth = { showPicker = true }
                    )
                }
            ) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    NavRow(vm)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .swipeNav(vm)
                    ) {
                        when (vm.view) {
                            ViewMode.MONTH -> MonthView(vm, engine) { dayDetail = it }
                            ViewMode.WEEK -> WeekView(vm, engine) { selected = it }
                            ViewMode.DAY -> DayView(vm, engine) { selected = it }
                            ViewMode.AGENDA -> AgendaView(vm, engine) { selected = it }
                        }
                    }
                }
            }
        }

        dayDetail?.let { d ->
            DaySheet(
                date = d, engine = engine, enabled = vm.filters.toMap(),
                onEvent = { selected = it },
                onOpenDay = { dayDetail = null; vm.goDay(d) },
                onDismiss = { dayDetail = null }
            )
        }
        selected?.let { EventDialog(it, engine) { selected = null } }
        if (showSettings) SettingsDialog(vm) { showSettings = false }
        if (showPicker) {
            MonthPicker(vm.cursor, onDismiss = { showPicker = false }) {
                vm.cursor = it
                vm.view = ViewMode.MONTH
                vm.save()
                showPicker = false
            }
        }
    }
}

/** החלקה בין חודשים/ימים (RTL: החלקה ימינה = הבא) */
private fun Modifier.swipeNav(vm: AppViewModel): Modifier = pointerInput(vm.view) {
    if (vm.view == ViewMode.WEEK) return@pointerInput // בשבוע יש גלילה אופקית
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total > 140f) vm.shift(1) else if (total < -140f) vm.shift(-1)
        },
        onHorizontalDrag = { _, dx -> total += dx }
    )
}

/* ---------- כותרת עליונה ---------- */

fun titleAndSub(vm: AppViewModel, engine: Engine): Pair<String, String> {
    val c = vm.cursor
    return when (vm.view) {
        ViewMode.MONTH -> {
            val first = c.withDayOfMonth(1)
            val last = c.withDayOfMonth(c.lengthOfMonth())
            first.monthYear() to engine.hebRange(first, last)
        }
        ViewMode.WEEK -> {
            val a = c.startOfWeek()
            val b = a.plusDays(6)
            val t = if (a.month == b.month) "${a.dayOfMonth}–${b.dayOfMonth} ${a.monthYear()}"
            else "${a.dayOfMonth} ${GREG_MONTHS[a.monthValue - 1]} – ${b.dayOfMonth} ${GREG_MONTHS[b.monthValue - 1]} ${b.year}"
            t to engine.hebRange(a, b)
        }
        ViewMode.DAY -> c.longDate() to "${c.weekdayName()} · ${engine.hebFull(c)}"
        ViewMode.AGENDA -> {
            val b = c.plusDays(29)
            "${c.dayOfMonth} ${GREG_MONTHS[c.monthValue - 1]} – ${b.dayOfMonth} ${GREG_MONTHS[b.monthValue - 1]} ${b.year}" to
                engine.hebRange(c, b)
        }
    }
}

/**
 * שורה 1: תפריט | כותרת החודש (לחיצה פותחת בורר חודש) | היום | הגדרות.
 * הכותרת היא האלמנט הבולט; שאר הפעולות קטנות ושקטות.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTopBar(
    vm: AppViewModel,
    engine: Engine,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onPickMonth: () -> Unit
) {
    val (title, sub) = titleAndSub(vm, engine)
    TopAppBar(
        title = {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "בחירת חודש", onClick = onPickMonth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        sub, style = JcText.hebDay, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "תפריט ראשי") }
        },
        actions = {
            FilledTonalButton(onClick = { vm.today() }, modifier = Modifier.heightIn(min = 40.dp)) { Text("היום") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "הגדרות") }
        }
    )
}

/**
 * שורה 2: חץ הקודם | בורר תצוגה | חץ הבא.
 * ב-RTL "הקודם" נמצא מימין. החצים אינם מתהפכים אוטומטית, לכן הכיוון נבחר במפורש.
 */
@Composable
fun NavRow(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vm.shift(-1) }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "הקודם")
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewMode.values().forEach { m ->
                FilterChip(
                    selected = vm.view == m,
                    onClick = {
                        vm.view = m
                        vm.save()
                    },
                    label = { Text(m.short, style = JcText.chip, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        IconButton(onClick = { vm.shift(1) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "הבא")
        }
    }
}

/* ---------- בורר חודש ושנה ---------- */
@Composable
private fun MonthPicker(current: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var year by remember { mutableStateOf(current.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { year -= 1 }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "שנה קודמת")
                }
                Text("$year", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = { year += 1 }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "שנה הבאה")
                }
            }
        },
        text = {
            Column {
                for (r in 0 until 4) {
                    Row(Modifier.fillMaxWidth()) {
                        for (k in 0 until 3) {
                            val m = r * 3 + k + 1
                            val sel = year == current.year && m == current.monthValue
                            TextButton(
                                onClick = { onPick(LocalDate.of(year, m, 1)) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text(
                                    GREG_MONTHS[m - 1], maxLines = 1,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

/* ---------- תפריט צד ---------- */
@Composable
private fun DrawerContent(vm: AppViewModel, engine: Engine, onSettings: () -> Unit) {
    val today = LocalDate.now()
    val next = remember(engine, today) { engine.nextShabbat(today, System.currentTimeMillis()) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                engine.hebFull(today),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${today.weekdayName()}, ${today.longDate()}",
                style = JcText.hebDay,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            next.parsha?.let {
                Spacer(Modifier.height(10.dp))
                Text(it.label, fontWeight = FontWeight.Medium)
            }
            next.candle?.let { ShabbatRow(it, engine) }
            next.havdalah?.let { ShabbatRow(it, engine) }
            Spacer(Modifier.height(10.dp))
            Text(
                "${engine.city.name} · ${vm.candleMinutes} דק׳ לפני השקיעה",
                style = JcText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("יומנים", fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
        Cat.values().forEach { cat ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable {
                        vm.filters[cat] = !(vm.filters[cat] ?: true)
                        vm.save()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = vm.filters[cat] ?: true,
                    onCheckedChange = {
                        vm.filters[cat] = it
                        vm.save()
                    },
                    colors = CheckboxDefaults.colors(checkedColor = cat.color, uncheckedColor = cat.color)
                )
                Text(cat.label, fontSize = 14.sp)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("הגדרות ומיקום", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun ShabbatRow(e: Ev, engine: Engine) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.label, fontWeight = FontWeight.Medium)
            Text(
                "${e.date.weekdayName()}, ${e.date.longDate()}",
                style = JcText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(engine.hm(e.timeMillis ?: 0L), fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

/* ---------- פרטי אירוע ---------- */
@Composable
private fun EventDialog(e: Ev, engine: Engine, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(e.cat.color, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (e.cat == Cat.SHABBAT || e.cat == Cat.ZMANIM) e.label else e.full,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(Modifier.semantics { contentDescription = "פרטי אירוע" }) {
                Text(
                    "${e.date.weekdayName()}, ${e.date.longDate()} · ${engine.hebFull(e.date)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = JcText.hebDay
                )
                e.timeMillis?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(engine.hm(it), fontSize = 28.sp, fontWeight = FontWeight.Medium)
                }
                if (e.memo.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(e.memo)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${e.cat.label} · ${engine.city.name}",
                    style = JcText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
