package com.nofollow.app.features.home.domain.model

/** Akun yang sedang dipantau, versi yang dibutuhkan Beranda. */
data class HomeProfile(
    val username: String,
    /** Path file foto di penyimpanan app; null kalau foto belum ada. */
    val avatarPath: String?,
    /** Berubah tiap foto diganti; dipakai UI sebagai kunci muat ulang. */
    val avatarVersion: Long,
)

/** Empat angka di Beranda. Yang bergantung pada daftar following null sampai daftar itu pernah diambil. */
data class HomeAudience(
    /** Kamu ikuti, tidak mengikuti kamu. Bisa meleset beberapa akun karena IG menyaring sebagian daftar. */
    val notFollowingBack: Int?,
    /** Unfollow (termasuk akun hilang/nonaktif) dalam 30 hari terakhir. */
    val unfollowers: Int,
    val unfollowersThisWeek: Int,
    /** Mengikuti kamu, tidak kamu ikuti. */
    val fans: Int?,
    /** Saling follow. */
    val mutual: Int?,
    /** Persen following yang juga follow kamu (0-100). Null kalau daftar following belum ada. */
    val mutualPercent: Float?,
    val followingSyncedAt: Long?,
)

/** Data baru per menu sejak menu itu terakhir dilihat di Tracker; isi lencana kecil di menu Beranda. Nol = tanpa lencana. */
data class HomeMenuBadges(
    val notFollowingBack: Int = 0,
    val unfollowers: Int = 0,
    val fans: Int = 0,
    val mutual: Int = 0,
)

/** Semua yang ditampilkan Beranda. Profil/audience null selama belum login atau datanya belum ada. */
data class HomeOverview(
    val profile: HomeProfile?,
    val audience: HomeAudience?,
    /** Sinkron terakhir yang berhasil: sweep follower atau following, mana yang lebih baru. */
    val lastSyncedAt: Long?,
    /** Aktivitas terbaru, paling baru di atas, maksimal [HomeOverview.ACTIVITY_LIMIT]. */
    val recentActivity: List<HomeActivity> = emptyList(),
    /** Null selama belum ada riwayat sama sekali. */
    val growth: HomeGrowth? = null,
    /** Null selama status sinkron belum terbaca. */
    val syncProgress: HomeSyncProgress? = null,
    val menuBadges: HomeMenuBadges = HomeMenuBadges(),
) {
    companion object {
        const val ACTIVITY_LIMIT = 5
    }
}