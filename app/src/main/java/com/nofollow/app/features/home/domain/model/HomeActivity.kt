package com.nofollow.app.features.home.domain.model

enum class HomeActivityType { UNFOLLOW, FOLLOW, REFOLLOW, RENAME, UNAVAILABLE }

/** Satu baris di "Aktivitas Terbaru". */
data class HomeActivity(
    val id: Long,
    /** ID numerik akun; kunci untuk foto, tetap walau username berganti. */
    val pk: String,
    val type: HomeActivityType,
    /** Username pada saat kejadian (untuk RENAME: username baru). */
    val username: String,
    /** Hanya RENAME. */
    val previousUsername: String?,
    val occurredAt: Long,
    /** Path file foto akun ini; null kalau belum ada, UI jatuh ke huruf awal. */
    val avatarPath: String?,
    /** Apakah kamu mengikuti akun ini (dari daftar following tersimpan); null kalau daftar itu belum pernah diambil. */
    val iFollow: Boolean? = null,
)