package com.nofollow.app.features.tracker.domain.model

private const val HOUR_MS = 3_600_000L

/**
 * Kecepatan sweep otomatis. Interval dasar; jitter dan penalti throttle ditambahkan di SyncPlanner.
 * Latensi deteksi unfollow kira-kira setengah interval + durasi sweep.
 */
enum class SyncMode(val key: String, val label: String, val intervalMs: Long) {
    HEMAT("hemat", "Hemat", 12 * HOUR_MS),
    NORMAL("normal", "Normal", 6 * HOUR_MS),
    CEPAT("cepat", "Cepat", 3 * HOUR_MS),
    KILAT("kilat", "Kilat", 2 * HOUR_MS);

    companion object {
        val DEFAULT = NORMAL

        fun fromKey(key: String?): SyncMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}