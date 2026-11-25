package com.menipdf.jewishcalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * מערכת טיפוגרפיה אחידה ללוח. כל הגדלים ביחידות sp (מתאימים להגדלת גופן במערכת)
 * והגודל המינימלי בלוח הוא 11sp.
 */
object JcText {
    /** תאריך לועזי – מזהה היום הראשי */
    val gregDay = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)

    /** תאריך עברי – משני אך קריא */
    val hebDay = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)

    /** תווית אירוע */
    val chip = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp)

    /** תווית קטנה (המינימום בלוח) */
    val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)

    /** שעת כניסה/יציאה */
    val timeValue = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)

    /** ימות השבוע בכותרת הלוח */
    val weekday = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
}

@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** צבע טקסט על רקע "טונאלי" – בהיר יותר במצב כהה כדי לשמור על ניגודיות */
fun tonalText(cat: Cat, dark: Boolean): Color = if (dark) lerp(cat.color, Color.White, 0.6f) else cat.color

/**
 * שפה חזותית אחידה לקטגוריות:
 * מלא = מועד (חג / צום / ראש חודש); טונאלי = מידע שוטף (פרשה, עומר, דף יומי).
 * ההבדל בצורה (מלא/טונאלי) בנוסף לצבע – כדי לא להסתמך על צבע בלבד.
 */
fun isFilled(cat: Cat): Boolean = cat == Cat.HOLIDAY || cat == Cat.FAST || cat == Cat.RC

@Composable
fun EventChip(e: Ev, modifier: Modifier = Modifier, maxLines: Int = 2) {
    val dark = isDark()
    val filled = isFilled(e.cat)
    val bg = if (filled) e.cat.color else e.cat.color.copy(alpha = if (dark) 0.28f else 0.14f)
    val fg = if (filled) Color.White else tonalText(e.cat, dark)
    Text(
        text = e.label,
        style = JcText.chip.copy(fontWeight = if (e.chag) FontWeight.Bold else FontWeight.Medium),
        color = fg,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
