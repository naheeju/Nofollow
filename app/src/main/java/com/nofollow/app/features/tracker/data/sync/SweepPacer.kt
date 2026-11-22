package com.nofollow.app.features.tracker.data.sync

import com.nofollow.app.features.tracker.domain.model.Pacing
import kotlin.random.Random

/**
 * Pengatur jeda satu sweep: jeda acak antar halaman, istirahat panjang berkala, jeda "mikir" sesekali, dan rem
 * otomatis kalau latensi request melonjak (tanda awal throttling: perlambat dulu sebelum kena 429).
 * Satu instance per jalan sweep; tidak dibagi lintas coroutine.
 */
class SweepPacer(private val pacing: Pacing) {
    private var avgLatencyMs = 0.0
    private var samples = 0
    private var brakeMs = 0L

    /** Catat lama satu request. Lonjakan dibanding rata-rata menambah jeda sebelum halaman berikutnya. */
    fun onLatency(ms: Long) {
        val slow = samples >= WARMUP_SAMPLES && ms > SLOW_FACTOR * avgLatencyMs && ms > SLOW_MIN_MS
        if (slow) brakeMs = (ms * BRAKE_FACTOR).toLong().coerceAtMost(BRAKE_MAX_MS)
        // Sampel lambat hanya ikut sedikit ke rata-rata supaya lonjakan berulang tetap terdeteksi.
        val weight = if (slow) 0.05 else 0.2
        avgLatencyMs = if (samples == 0) ms.toDouble() else avgLatencyMs * (1 - weight) + ms * weight
        samples++
    }

    /** Jeda sebelum halaman berikutnya (ms). [pagesThisRun] = halaman yang sudah diambil di jalan ini (>= 1). */
    fun nextDelayMs(pagesThisRun: Int): Long {
        val long = pacing.longPauseEveryPages > 0 && pagesThisRun % pacing.longPauseEveryPages == 0
        var ms = if (long) {
            Random.nextLong(pacing.longPauseMinMs, pacing.longPauseMaxMs + 1)
        } else {
            Random.nextLong(pacing.pageDelayMinMs, pacing.pageDelayMaxMs + 1)
        }
        if (!long && pacing.thinkPauseChance > 0 && Random.nextDouble() < pacing.thinkPauseChance) {
            ms += Random.nextLong(pacing.thinkPauseMinMs, pacing.thinkPauseMaxMs + 1)
        }
        ms += brakeMs
        brakeMs = 0
        return ms
    }

    private companion object {
        const val WARMUP_SAMPLES = 3
        const val SLOW_FACTOR = 2.5
        const val SLOW_MIN_MS = 1_500L
        const val BRAKE_FACTOR = 2.0
        const val BRAKE_MAX_MS = 20_000L
    }
}