package com.nofollow.app.features.tracker.domain.model

import com.nofollow.app.core.common.AppError

/** Satu akun yang kita ikuti (hasil sweep following). */
data class FollowingUser(
    val pk: String,
    val username: String,
    val fullName: String,
    val isPrivate: Boolean,
    val isVerified: Boolean,
)

sealed interface FollowingOutcome {
    /** Tidak ada request yang dikirim. [until] terisi untuk COOLING_DOWN dan DAILY_CAP. */
    data class Skipped(val reason: SkipReason, val until: Long? = null) : FollowingOutcome

    /** Batas halaman per jalan tercapai; sisanya dilanjutkan di jalan berikutnya dari cursor yang tersimpan. */
    data class Progress(val pagesDone: Int) : FollowingOutcome

    data class Completed(val count: Int) : FollowingOutcome

    /** Hasil terbaca jauh lebih sedikit dari daftar tersimpan: dianggap tidak lengkap, daftar lama dipertahankan. */
    data class Aborted(val fetched: Int, val previous: Int) : FollowingOutcome

    data class Halted(val reason: String) : FollowingOutcome

    data class Failed(val error: AppError, val retryAt: Long) : FollowingOutcome
}