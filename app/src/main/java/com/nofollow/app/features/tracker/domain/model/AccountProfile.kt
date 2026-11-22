package com.nofollow.app.features.tracker.domain.model

/** Identitas akun yang sedang dipantau, untuk ditampilkan di Beranda. */
data class AccountProfile(
    val username: String,
    /** Path file foto di penyimpanan app; null kalau foto belum ada. */
    val avatarPath: String?,
    /** Berubah tiap foto diganti; dipakai UI sebagai kunci muat ulang. */
    val avatarVersion: Long,
)