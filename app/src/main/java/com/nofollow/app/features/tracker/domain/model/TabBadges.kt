package com.nofollow.app.features.tracker.domain.model

/**
 * Jumlah data baru per kategori sejak kategori itu terakhir dilihat di layar Tracker; isi lencana kecil di menu Beranda.
 * Hanya empat kategori yang punya lencana. Nol = tidak ada lencana.
 */
data class TabBadges(
    /** Unfollow (termasuk akun hilang/nonaktif) yang terdeteksi sejak Unfollowers terakhir dilihat. */
    val unfollowers: Int = 0,
    /** Akun yang baru masuk daftar "kamu ikuti, tidak follow balik". */
    val notFollowingBack: Int = 0,
    /** Akun yang baru masuk daftar fans. */
    val fans: Int = 0,
    /** Akun yang baru masuk daftar mutual. */
    val mutual: Int = 0,
) {
    fun of(tab: TrackerTab): Int = when (tab) {
        TrackerTab.UNFOLLOWERS -> unfollowers
        TrackerTab.NOT_FOLLOWING_BACK -> notFollowingBack
        TrackerTab.FANS -> fans
        TrackerTab.MUTUAL -> mutual
        else -> 0
    }
}