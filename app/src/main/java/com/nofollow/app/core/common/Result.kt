package com.nofollow.app.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    /** Tidak ada koneksi, timeout, gagal memuat origin, atau server 5xx (sementara, aman di-retry dengan backoff). */
    data object Network : AppError

    /** Sesi tidak valid lagi (login_required / 401). Perlu login ulang. */
    data object SessionExpired : AppError

    /** Kena rate limit (429 / "wait a few minutes"). Perlu cooldown. */
    data object RateLimited : AppError

    /** Instagram minta verifikasi (checkpoint / challenge). Semua pengecekan otomatis harus berhenti. */
    data object Checkpoint : AppError

    /** Aksi ditandai mencurigakan (feedback_required). Perlu cooldown panjang. */
    data object ActionBlocked : AppError

    data class Unknown(val message: String? = null) : AppError
}