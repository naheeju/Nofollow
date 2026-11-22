package com.nofollow.app.features.tracker.domain.model

import com.nofollow.app.core.common.AppError

/** Siapa yang memicu sinkronisasi. Menentukan aturan jam tenang dan jadwal. */
enum class SyncTrigger {
    /** Worker terjadwal: hormati jam tenang dan jadwal sweep (nextSweepAt). */
    SCHEDULED,

    /** App dibuka: jalan kalau sweep terakhir sudah cukup lama (atau jadwal sudah lewat). Hormati jam tenang. */
    APP_OPEN,

    /** Tombol manual: boleh di jam tenang, ada jeda minimal antar sweep. */
    MANUAL,

    /** Pantau cepat: sweep berkala. Jam tenang sudah diperiksa probe sendiri; sweep jalan cepat. */
    PROBE,
}

enum class SkipReason { COOLING_DOWN, QUIET_HOURS, NOT_DUE, TOO_SOON, DAILY_CAP }

sealed interface SyncOutcome {
    /** Tidak ada request yang dikirim. [until] terisi untuk COOLING_DOWN, TOO_SOON, dan DAILY_CAP. */
    data class Skipped(val reason: SkipReason, val until: Long? = null) : SyncOutcome

    /** Batas per jalan tercapai; sisanya dilanjutkan di jalan berikutnya. */
    data class Progress(val pagesDone: Int, val confirmLeft: Int) : SyncOutcome

    /** Satu sweep selesai. [baseline] true = sweep pertama (hanya merekam daftar). */
    data class Completed(
        val baseline: Boolean,
        val unfollowed: List<String>,
        val unavailable: List<String>,
    ) : SyncOutcome

    /** Daftar yang terbaca tidak masuk akal (terlalu banyak yang hilang sekaligus); tidak ada perubahan disimpan. */
    data class Aborted(val missing: Int, val active: Int) : SyncOutcome

    /** Berhenti total sampai user bertindak (checkpoint / session habis / belum login). */
    data class Halted(val reason: String) : SyncOutcome

    data class Failed(val error: AppError, val retryAt: Long) : SyncOutcome
}

/** Hasil keputusan "apakah sweep cepat boleh jalan sekarang" (tanpa request ke Instagram). */
sealed interface ProbeResult {
    /** Tidak boleh jalan sekarang. [until] terisi kalau alasannya cooldown. */
    data class Skipped(val why: String, val until: Long? = null) : ProbeResult

    /** Boleh: mulai sweep cepat baru. */
    data object SweepDue : ProbeResult

    /** Sweep sebelumnya belum selesai (kena batas per jalan): lanjutkan. */
    data object SweepPending : ProbeResult

    data class Halted(val reason: String) : ProbeResult
}