package com.nofollow.app.features.home.domain.model

/** Jumlah follower pada satu hari ([day] = nomor hari lokal). */
data class GrowthPoint(val day: Long, val followers: Int)

/** Data kartu "Pertumbuhan Akun". */
data class HomeGrowth(
    /** Urut dari hari paling lama; satu titik per hari yang punya data. */
    val points: List<GrowthPoint>,
    /** Jumlah follower terbaru. */
    val current: Int,
    /** Selisih terbaru dengan titik pertama di jendela. Nol kalau baru ada satu titik. */
    val delta: Int,
    /** Persen perubahan; null kalau titik pertama 0. */
    val deltaPercent: Float?,
    /** Rentang hari antara titik pertama dan terakhir. */
    val spanDays: Int,
) {
    /** Grafik butuh minimal dua hari data. */
    val hasChart: Boolean get() = points.size >= 2
}