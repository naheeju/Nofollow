package com.nofollow.app.features.home.domain.model

enum class HomeSyncSkip { COOLING_DOWN, QUIET_HOURS, NOT_DUE, TOO_SOON, DAILY_CAP }

/** Hasil satu kali "sinkron semua" (follower, following, lalu Kepo), diringkas jadi satu. */
sealed interface HomeSyncResult {
    /** [kepoInteractions] = like/balasan story baru dari Kepo; null kalau Kepo tidak ikut jalan atau tidak selesai. */
    data class Synced(val kepoInteractions: Int? = null) : HomeSyncResult

    /** Batas per jalan tercapai; sisanya dilanjutkan otomatis. */
    data object InProgress : HomeSyncResult

    /** [until] (epoch ms) terisi untuk COOLING_DOWN, TOO_SOON, dan DAILY_CAP: kapan boleh sinkron lagi. */
    data class Skipped(val reason: HomeSyncSkip, val until: Long? = null) : HomeSyncResult

    /** Daftar yang terbaca tidak masuk akal, tidak ada yang diubah. */
    data object Aborted : HomeSyncResult

    /** Berhenti sampai user bertindak (checkpoint, sesi habis, belum login). */
    data class Halted(val reason: String) : HomeSyncResult

    data object Failed : HomeSyncResult
}