package com.menipdf.jewishcalendar

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("jcal", Context.MODE_PRIVATE)

    var view by mutableStateOf(
        runCatching { ViewMode.valueOf(sp.getString("view", "MONTH") ?: "MONTH") }.getOrDefault(ViewMode.MONTH)
    )
    var cursor by mutableStateOf(LocalDate.now())
    var cityId by mutableStateOf(sp.getString("cityId", "jerusalem") ?: "jerusalem")
    var custom by mutableStateOf<City?>(loadCustom())
    var candleOverride by mutableStateOf(sp.getInt("candle", -1))
    var havdalah by mutableStateOf(sp.getString("havdalah", "8.5") ?: "8.5")
    var theme by mutableStateOf(sp.getString("theme", "auto") ?: "auto")
    val filters = mutableStateMapOf<Cat, Boolean>().apply {
        Cat.values().forEach { put(it, sp.getBoolean("f_${it.name}", it != Cat.DAF)) }
    }

    val city: City
        get() {
            val c = custom
            if (cityId == "custom" && c != null) return c
            return CITIES.firstOrNull { it.id == cityId } ?: CITIES[0]
        }
    val candleMinutes: Int get() = if (candleOverride >= 0) candleOverride else city.mins

    private fun loadCustom(): City? {
        val s = sp.getString("custom", null) ?: return null
        val p = s.split("|")
        if (p.size < 5) return null
        return runCatching {
            City("custom", p[0], p[1].toDouble(), p[2].toDouble(), p[3], p[4] == "1", 18, "מיקום")
        }.getOrNull()
    }

    fun save() {
        val e = sp.edit()
        e.putString("view", view.name)
        e.putString("cityId", cityId)
        e.putInt("candle", candleOverride)
        e.putString("havdalah", havdalah)
        e.putString("theme", theme)
        custom?.let { e.putString("custom", "${it.name}|${it.lat}|${it.lon}|${it.tz}|${if (it.il) "1" else "0"}") }
        filters.forEach { (k, v) -> e.putBoolean("f_${k.name}", v) }
        e.apply()
    }

    fun setCity(id: String) {
        cityId = id
        candleOverride = -1
        save()
    }

    fun setCustom(c: City) {
        custom = c
        cityId = "custom"
        candleOverride = -1
        save()
    }

    fun today() { cursor = LocalDate.now() }

    fun goDay(d: LocalDate) {
        cursor = d
        view = ViewMode.DAY
        save()
    }

    fun shift(dir: Int) {
        cursor = when (view) {
            ViewMode.MONTH -> cursor.plusMonths(dir.toLong())
            ViewMode.WEEK -> cursor.plusWeeks(dir.toLong())
            ViewMode.DAY -> cursor.plusDays(dir.toLong())
            ViewMode.AGENDA -> cursor.plusDays(14L * dir)
        }
    }
}
