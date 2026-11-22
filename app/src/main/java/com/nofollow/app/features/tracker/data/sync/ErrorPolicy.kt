package com.nofollow.app.features.tracker.data.sync

import com.nofollow.app.core.common.AppError
import com.nofollow.app.features.tracker.domain.model.SyncConfig

/** Hasil keputusan setelah sebuah request gagal. */
data class ErrorAction(
    /** Tidak ada request sebelum now + cooldownMs. */
    val cooldownMs: Long,
    val nextLevel: Int,
    /** Non-null = berhenti total sampai user bertindak. */
    val haltReason: String?,
)

object ErrorPolicy {
    const val HALT_CHECKPOINT = "checkpoint"
    const val HALT_SESSION_EXPIRED = "session_expired"
    /** Tampil di UI sebagai "Berhenti: <alasan>.", jadi ditulis sebagai kalimat. */
    const val HALT_ACCOUNT_TOO_LARGE = "akun melebihi batas 5.000 follower atau following"

    /**
     * [userWaiting] true = sinkron pertama (baseline) atau tombol manual: ada orang yang menunggu di layar.
     * Error jaringan murni (offline, WebView beku, timeout) bukan sinyal dari Instagram, jadi untuk mereka tidak ada
     * cooldown: tombol bisa langsung ditekan lagi dan sweep lanjut dari cursor yang sudah tersimpan. Untuk sinkron
     * otomatis cooldown-nya pendek ([SyncConfig.autoNetworkCooldownMs]) supaya tidak mengetuk terus saat sinyal jelek.
     * Error dari Instagram (429, checkpoint, session, feedback_required) tidak terpengaruh.
     */
    fun onError(error: AppError, level: Int, config: SyncConfig, userWaiting: Boolean = false): ErrorAction = when (error) {
        AppError.RateLimited -> {
            val ladder = config.rateLimitLadderMs
            ErrorAction(ladder[level.coerceIn(0, ladder.lastIndex)], level + 1, null)
        }
        AppError.Checkpoint -> ErrorAction(0, level, HALT_CHECKPOINT)
        AppError.SessionExpired -> ErrorAction(0, level, HALT_SESSION_EXPIRED)
        AppError.ActionBlocked -> ErrorAction(config.actionBlockedCooldownMs, level, null)
        AppError.Network -> ErrorAction(if (userWaiting) 0 else config.autoNetworkCooldownMs, level, null)
        is AppError.Unknown -> ErrorAction(config.unknownCooldownMs, level, null)
    }
}