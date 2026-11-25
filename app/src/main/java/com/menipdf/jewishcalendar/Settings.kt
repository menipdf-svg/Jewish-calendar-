package com.menipdf.jewishcalendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.TimeZone

private val HAVDALAH_OPTIONS = listOf(
    "8.5" to "8.5° מתחת לאופק (ברירת מחדל)",
    "42" to "42 דקות אחרי השקיעה",
    "50" to "50 דקות אחרי השקיעה",
    "72" to "72 דקות אחרי השקיעה (רבנו תם)"
)
private val THEME_OPTIONS = listOf("auto" to "לפי המכשיר", "light" to "בהיר", "dark" to "כהה")

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var pickCity by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var minsText by remember(vm.cityId, vm.candleOverride) { mutableStateOf(vm.candleMinutes.toString()) }

    fun applyLocation(loc: Location?) {
        if (loc == null) {
            msg = "לא נמצא מיקום אחרון במכשיר. פתחו מפות ונסו שוב."
            return
        }
        val tz = TimeZone.getDefault().id
        vm.useCustom(City("custom", "המיקום שלי", loc.latitude, loc.longitude, tz, tz == "Asia/Jerusalem", 18, "מיקום"))
        msg = "המיקום עודכן."
    }

    fun fetchLocation() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull {
                try {
                    lm.getLastKnownLocation(it)
                } catch (e: SecurityException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            .maxByOrNull { it.time }
        applyLocation(best)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) fetchLocation() else msg = "אין הרשאת מיקום."
    }

    if (pickCity) {
        CityPicker(vm) { pickCity = false }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        title = { Text("הגדרות") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("מיקום", fontWeight = FontWeight.Medium)
                Row(
                    Modifier.fillMaxWidth().clickable { pickCity = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Text("  ${vm.city.name}", fontSize = 16.sp)
                }
                OutlinedButton(onClick = {
                    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) fetchLocation() else permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text("השתמש במיקום שלי") }
                if (msg.isNotEmpty()) {
                    Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = minsText,
                    onValueChange = { t ->
                        minsText = t.filter { it.isDigit() }.take(2)
                        vm.candleOverride = minsText.toIntOrNull() ?: -1
                        vm.save()
                    },
                    label = { Text("הדלקת נרות – דקות לפני השקיעה") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("צאת הכוכבים / הבדלה", fontWeight = FontWeight.Medium)
                HAVDALAH_OPTIONS.forEach { (k, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.havdalah = k; vm.save() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = vm.havdalah == k, onClick = { vm.havdalah = k; vm.save() })
                        Text(label, fontSize = 14.sp)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("ערכת נושא", fontWeight = FontWeight.Medium)
                THEME_OPTIONS.forEach { (k, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.theme = k; vm.save() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = vm.theme == k, onClick = { vm.theme = k; vm.save() })
                        Text(label, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "הזמנים מחושבים לפי קואורדינטות המיקום שנבחר. לפסיקה הלכתית יש להיוועץ ברב.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun CityPicker(vm: AppViewModel, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = { TextButton(onClick = onDone) { Text("סגור") } },
        title = { Text("בחירת עיר") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CITIES.groupBy { it.group }.forEach { (group, list) ->
                    Text(
                        group, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    list.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setCity(c.id); onDone() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = vm.cityId == c.id, onClick = { vm.setCity(c.id); onDone() })
                            Text(c.name, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    )
}
