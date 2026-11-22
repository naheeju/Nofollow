package com.nofollow.app.features.home.domain.model

import kotlin.math.ceil

/**
 * Kemajuan sinkron untuk kartu progres di Beranda, dibaca dari status tracker (bukan dari hasil sinkron).
 * Saat baseline berjalan, [followersRead] tumbuh tiap halaman dan belum menunjukkan total sebenarnya.
 */
data class HomeSyncProgress(
    /** False sampai sweep follower pertama selesai. */
    val baselineDone: Boolean,
    /** Sweep follower sedang berjalan (membaca halaman atau konfirmasi). False = tahap following atau belum mulai. */
    val sweepActive: Boolean,
    val pagesDone: Int,
    /** Follower aktif yang sudah tercatat. */
    val followersRead: Int,
    /** Jumlah following dari daftar tersimpan; null selama daftar following belum pernah terbaca. */
    val followingCount: Int? = null,
) {
    /** Perkiraan halaman total untuk sweep follower biasa (setelah baseline, [followersRead] sudah total). */
    val expectedPages: Int get() = ceil(followersRead / USERS_PER_PAGE).toInt() + 1

    /** Kemajuan 0..0,95 untuk sweep biasa; null kalau belum bisa dihitung (baseline atau tahap following). */
    val fraction: Float?
        get() = if (baselineDone && sweepActive && followersRead > 0) {
            (pagesDone.toFloat() / expectedPages).coerceIn(0f, 0.95f)
        } else {
            null
        }

    /** Perkiraan halaman sweep following dari jumlah following tersimpan; null kalau daftarnya belum pernah terbaca. */
    val expectedFollowingPages: Int? get() = followingCount?.let { ceil(it / USERS_PER_PAGE).toInt() + 1 }

    /**
     * Perkiraan menit sinkron biasa dari jumlah nyata: halaman follower ditambah halaman following (kalau jumlahnya
     * sudah diketahui), masing-masing ~0,9-1,8 detik per halaman termasuk waktu request. Kasar saja; tergantung
     * kecepatan Instagram dan jeda acak.
     */
    fun regularMinutes(): IntRange {
        val pages = expectedPages + (expectedFollowingPages ?: 0)
        val lo = maxOf(1, ceil(pages * 0.9 / 60).toInt())
        val hi = maxOf(1, ceil(pages * 1.8 / 60).toInt())
        return lo..maxOf(hi, lo + 1)
    }

    private companion object {
        /** User nyata rata-rata per halaman (IG membatasi ~25 dan sering menyaring). */
        const val USERS_PER_PAGE = 16.0
    }
}