package com.example.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatters {
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    fun formatMessageTime(ts: Timestamp?): String {
        val date = ts?.toDate() ?: return ""
        return timeFormat.format(date)
    }

    fun formatChatListTime(ts: Timestamp?): String {
        val date = ts?.toDate() ?: return ""
        val nowCal = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }
        return when {
            isSameDay(nowCal, msgCal) -> timeFormat.format(date)
            isYesterday(nowCal, msgCal) -> "Yesterday"
            nowCal.get(Calendar.WEEK_OF_YEAR) == msgCal.get(Calendar.WEEK_OF_YEAR) &&
                nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> dayFormat.format(date)
            else -> dateFormat.format(date)
        }
    }

    fun formatLastSeen(ts: Timestamp?): String {
        val date = ts?.toDate() ?: return "last seen recently"
        val nowCal = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }
        return when {
            isSameDay(nowCal, msgCal) -> "last seen today at ${timeFormat.format(date)}"
            isYesterday(nowCal, msgCal) -> "last seen yesterday at ${timeFormat.format(date)}"
            else -> "last seen ${dateFormat.format(date)} at ${timeFormat.format(date)}"
        }
    }

    fun formatDurationSeconds(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    fun formatDurationMs(ms: Long): String {
        return formatDurationSeconds((ms / 1000L).coerceAtLeast(0L))
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val y = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(y, target)
    }
}
