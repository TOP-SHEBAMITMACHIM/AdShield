package com.adshield.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    fun count(value: Long): String = when {
        value < 1_000 -> value.toString()
        value < 1_000_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    }

    fun dayKey(timeMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMs))

    fun time(timeMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))

    fun relative(timeMs: Long): String {
        val diff = (System.currentTimeMillis() - timeMs).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "${diff / 1_000L}s"
            diff < 3_600_000L -> "${diff / 60_000L}m"
            diff < 86_400_000L -> "${diff / 3_600_000L}h"
            else -> "${diff / 86_400_000L}d"
        }
    }

    fun lastSevenDayKeys(): List<String> {
        val out = ArrayList<String>(7)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        repeat(7) {
            out += fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    fun weekdayLabels(): List<String> {
        val out = ArrayList<String>(7)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        repeat(7) {
            out += fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }
}
