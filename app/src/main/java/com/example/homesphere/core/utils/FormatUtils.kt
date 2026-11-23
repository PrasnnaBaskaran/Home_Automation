package com.example.homesphere.core.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow

object FormatUtils {
    fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
    }

    fun formatFullDateTime(millis: Long): String {
        val formatter = SimpleDateFormat("EEE, MMM d • hh:mm a", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun formatShortTime(millis: Long): String {
        val formatter = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun formatTimestamp(millis: Long): String {
        val formatter = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    /**
     * Formats a timestamp as a live relative string such as "Just now", "5 seconds ago",
     * "2 minutes ago", etc. Pass a live ticking `nowMillis` from Compose so it updates every second.
     */
    fun formatRelativeTime(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = (nowMillis - millis).coerceAtLeast(0L)
        return when {
            diff < 2_000L -> "Just now"
            diff < 60_000L -> {
                val seconds = (diff / 1_000L).coerceAtLeast(1L)
                if (seconds == 1L) "1 second ago" else "$seconds seconds ago"
            }
            diff < 3_600_000L -> {
                val minutes = diff / 60_000L
                if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            }
            diff < 86_400_000L -> {
                val hours = diff / 3_600_000L
                if (hours == 1L) "1 hour ago" else "$hours hours ago"
            }
            else -> {
                val days = diff / 86_400_000L
                if (days == 1L) "1 day ago" else "$days days ago"
            }
        }
    }

    /**
     * Normalizes user-entered ESP8266 / Router IP or host:port input, stripping optional
     * scheme prefixes and trailing slashes while rejecting whitespace or control characters.
     */
    fun normalizeHostInput(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .trim()
        if (cleaned.isEmpty() || cleaned.any { it.isWhitespace() }) return ""
        return cleaned
    }

    fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        return when {
            days > 0 -> "${days}d ${hours}h ${mins}m"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    fun rssiQualityLabel(rssi: Int): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -62 -> "Very Good"
        rssi >= -72 -> "Fair"
        rssi >= -82 -> "Weak"
        else -> "Poor"
    }

    /**
     * Free-space path loss approximation for 2.4GHz / 5GHz indoor Wi-Fi signal strength.
     */
    fun estimateDistanceMeters(rssiDbm: Int, freqMhz: Int = 2437): Float {
        if (rssiDbm >= 0) return 0f
        val exp = (27.55 - (20 * kotlin.math.log10(freqMhz.toDouble())) + kotlin.math.abs(rssiDbm)) / 20.0
        return 10.0.pow(exp).toFloat().coerceIn(0.4f, 60f)
    }
}
