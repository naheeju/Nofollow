package com.nofollow.app.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val DAY_MS = 86_400_000L

/** Nomor hari menurut zona waktu perangkat. Dipakai untuk menghitung plafon request harian. */
fun localDayNumber(epochMs: Long): Long =
    (epochMs + TimeZone.getDefault().getOffset(epochMs)) / DAY_MS

/** Awal hari berikutnya (00:00 lokal) dalam epoch ms. */
fun nextLocalDayStart(epochMs: Long): Long {
    val offset = TimeZone.getDefault().getOffset(epochMs)
    return ((epochMs + offset) / DAY_MS + 1) * DAY_MS - offset
}

fun formatStamp(epochMs: Long): String =
    SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(epochMs))

/** "Baru saja", "5 mnt lalu", "3 j lalu", "Kemarin", "4 hari lalu". */
fun formatAgo(at: Long, now: Long): String {
    val minutes = (now - at).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes mnt lalu"
        minutes < 60 * 24 -> "${minutes / 60} j lalu"
        minutes < 60 * 48 -> "Kemarin"
        else -> "${minutes / (60 * 24)} hari lalu"
    }
}
