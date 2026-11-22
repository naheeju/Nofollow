package com.nofollow.app.features.tracker.data.sync

import com.nofollow.app.features.tracker.data.local.SyncStateEntity
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.domain.model.SyncMode
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import kotlin.random.Random

sealed interface SyncDecision {
    data class Halt(val reason: String) : SyncDecision
    data class CoolingDown(val until: Long) : SyncDecision
    /** Tombol manual ditekan terlalu cepat setelah sweep terakhir. */
    data class TooSoon(val until: Long) : SyncDecision
    data object QuietHours : SyncDecision
    /** Sweep sebelumnya belum selesai dan masih segar: lanjutkan dari cursor / tahap konfirmasi. */
    data object Resume : SyncDecision
    /** Mulai sweep baru (baseline kalau belum pernah selesai). */
    data class Start(val baseline: Boolean) : SyncDecision
    data object Idle : SyncDecision
}

object SyncPlanner {
    fun decide(
        state: SyncStateEntity?,
        now: Long,
        hourOfDay: Int,
        trigger: SyncTrigger,
        config: SyncConfig,
    ): SyncDecision {
        state?.haltReason?.let { return SyncDecision.Halt(it) }
        if (state != null && now < state.cooldownUntil) return SyncDecision.CoolingDown(state.cooldownUntil)
        // Worker selalu menghormati jam tenang, termasuk saat baseline dicicil. Buka app saat baseline belum
        // selesai tidak menunggu jam tenang: user ada di layar. Tombol manual boleh kapan saja.
        val firstSync = state == null || !state.baselineDone
        val quietApplies = trigger == SyncTrigger.SCHEDULED || (trigger == SyncTrigger.APP_OPEN && !firstSync)
        if (quietApplies && inQuietHours(hourOfDay, config)) {
            return SyncDecision.QuietHours
        }

        if (state == null) return SyncDecision.Start(baseline = true)

        // Setelah baseline, buka app tidak memulai atau melanjutkan sweep: cek cepat = tombol manual,
        // notifikasi = probe. Kalau tidak, sweep pelan dari buka app menahan tombol manual (mutex).
        if (trigger == SyncTrigger.APP_OPEN && state.baselineDone) return SyncDecision.Idle

        if (state.sweepInProgress) {
            val started = state.sweepStartedAt ?: 0L
            // Baseline tidak mendeteksi apa pun, jadi daftar yang dicicil berhari-hari tidak pernah "basi".
            val tooOld = state.baselineDone && now - started > config.maxSweepAgeMs
            return if (tooOld) SyncDecision.Start(baseline = false) else SyncDecision.Resume
        }

        if (!state.baselineDone) return SyncDecision.Start(baseline = true)

        val last = state.lastCompletedSweepAt
        val scheduledDue = last == null || (state.nextSweepAt?.let { now >= it } ?: true)

        return when (trigger) {
            SyncTrigger.SCHEDULED -> if (scheduledDue) SyncDecision.Start(false) else SyncDecision.Idle

            // Jarak probe dihitung dari sweep terakhir apa pun: habis cek manual, probe menunggu.
            SyncTrigger.PROBE -> {
                val until = last?.plus(config.probeSweepMinGapMs)
                if (until != null && now < until) SyncDecision.TooSoon(until) else SyncDecision.Start(false)
            }

            SyncTrigger.MANUAL -> {
                val until = last?.plus(config.manualMinGapMs)
                if (until != null && now < until) SyncDecision.TooSoon(until) else SyncDecision.Start(false)
            }

            // APP_OPEN sudah ditangani di atas (setelah baseline selalu Idle).
            else -> SyncDecision.Idle
        }
    }

    /** Jadwal sweep berikutnya: interval mode (dilipat saat penalti) dengan jitter acak. */
    fun nextSweepAt(from: Long, mode: SyncMode, penaltyActive: Boolean, config: SyncConfig): Long {
        val base = mode.intervalMs * (if (penaltyActive) config.penaltyIntervalMultiplier else 1)
        val jitter = 1.0 + Random.nextDouble(-config.intervalJitter, config.intervalJitter)
        return from + (base * jitter).toLong()
    }

    fun inQuietHours(hour: Int, config: SyncConfig): Boolean =
        inQuietWindow(hour, config.quietStartHour, config.quietEndHour)

    fun inQuietWindow(hour: Int, startHour: Int, endHour: Int): Boolean =
        if (startHour == endHour) false
        else if (startHour < endHour) hour in startHour until endHour
        else hour !in endHour..<startHour
}