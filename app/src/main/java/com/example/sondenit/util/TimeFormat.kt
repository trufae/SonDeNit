package com.example.sondenit.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_OF_DAY = SimpleDateFormat("HH:mm", Locale("ca"))
private val TIME_OF_DAY_LONG = SimpleDateFormat("HH:mm:ss", Locale("ca"))
private val DATE_LONG = SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale("ca"))

fun formatDurationShort(ms: Long): String {
    if (ms <= 0) return "0 min"
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}min"
        h > 0 -> "${h}h"
        m > 0 -> "${m}min"
        else -> "${ms / 1000}s"
    }
}

fun formatTimer(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
    else "%02d:%02d".format(m, sec)
}

fun formatTimeOfDay(ts: Long): String = TIME_OF_DAY.format(Date(ts))
fun formatTimeOfDayLong(ts: Long): String = TIME_OF_DAY_LONG.format(Date(ts))
fun formatDateLong(ts: Long): String = DATE_LONG.format(Date(ts)).replaceFirstChar { it.uppercase() }
