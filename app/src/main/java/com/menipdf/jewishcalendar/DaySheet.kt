package com.menipdf.jewishcalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** פירוט יום: כל האירועים, כניסת/יציאת שבת וזמני היום. נפתח בלחיצה על יום או על "+N עוד". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    date: LocalDate,
    engine: Engine,
    enabled: Map<Cat, Boolean>,
    onEvent: (Ev) -> Unit,
    onOpenDay: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val events = remember(engine, date, enabled) {
        engine.events(date).filter { enabled[it.cat] ?: true }
            .sortedWith(compareBy({ it.timeMillis != null }, { CAT_ORDER.indexOf(it.cat) }, { it.timeMillis ?: 0L }))
    }
    val zm = remember(engine, date) { engine.zmanim(date) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                engine.hebFull(date), fontSize = 24.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${date.weekdayName()}, ${date.longDate()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = JcText.hebDay
            )
            Spacer(Modifier.height(16.dp))
            if (events.isEmpty()) {
                Text("אין אירועים ביום הזה.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            events.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onEvent(e) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).background(e.cat.color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(e.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    e.timeMillis?.let {
                        Text(engine.hm(it), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "זמני היום · ${engine.city.name}", style = JcText.chip,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            zm.forEach { z ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(z.label)
                    Text(engine.hm(z.timeMillis), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenDay, modifier = Modifier.fillMaxWidth()) { Text("פתח תצוגת יום") }
        }
    }
}
