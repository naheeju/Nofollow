package com.nofollow.app.features.tracker.domain.model

/** Angka untuk empat kartu di layar utama. Yang bergantung pada daftar following null sampai daftar itu pernah diambil. */
data class AudienceSummary(
    val followers: Int,
    val following: Int?,
    /** Saling follow. */
    val mutual: Int?,
    /** Mengikuti kamu, tidak kamu ikuti. */
    val fans: Int?,
    /** Kamu ikuti, tidak mengikuti kamu. Bisa meleset beberapa akun karena IG menyaring sebagian daftar follower. */
    val notFollowingBack: Int?,
    /** Unfollow (termasuk akun hilang/nonaktif) dalam 30 hari terakhir. */
    val unfollowers: Int,
    val unfollowersThisWeek: Int,
    val followingSyncedAt: Long?,
    /** ID akun yang kamu ikuti (dari daftar following tersimpan); null selama daftar itu belum pernah diambil. */
    val followingPks: Set<String>? = null,
)