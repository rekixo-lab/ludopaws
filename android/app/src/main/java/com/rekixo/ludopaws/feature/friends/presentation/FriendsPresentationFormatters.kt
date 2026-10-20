package com.rekixo.ludopaws.feature.friends.presentation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Presentation-only relative time labels. Injecting nowMillis keeps tests deterministic
 * while default arguments preserve the existing production call sites and UX.
 */
internal fun formatLastSeen(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "Offline"
        val delta = (nowMillis - timestamp).coerceAtLeast(0L)
        return when {
            delta < 60_000L -> "Offline • Last seen just now"
            delta < 3_600_000L -> "Offline • Last seen ${delta / 60_000L}m ago"
            delta < 86_400_000L -> "Offline • Last seen ${delta / 3_600_000L}h ago"
            else -> "Offline • Last seen ${delta / 86_400_000L}d ago"
        }
    }

internal fun formatPlayed(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "Played recently"
        val delta = (nowMillis - timestamp).coerceAtLeast(0L)
        return when {
            delta < 60_000L -> "Played just now"
            delta < 3_600_000L -> "Played ${delta / 60_000L}m ago"
            delta < 86_400_000L -> "Played ${delta / 3_600_000L}h ago"
            else -> "Played ${delta / 86_400_000L}d ago"
        }
    }

internal fun formatMessageTime(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return ""
        val delta = (nowMillis - timestamp).coerceAtLeast(0L)
        return when {
            delta < 60_000L -> "just now"
            delta < 3_600_000L -> "${delta / 60_000L}m ago"
            delta < 86_400_000L -> "${delta / 3_600_000L}h ago"
            else -> "${delta / 86_400_000L}d ago"
        }
    }

internal fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        val out = ArrayList<JSONObject>(length())
        for (index in 0 until length()) {
            optJSONObject(index)?.let(out::add)
        }
        return out
    }
