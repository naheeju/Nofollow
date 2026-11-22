package com.nofollow.app.features.home.domain.repository

import com.nofollow.app.features.home.domain.model.HomeSyncResult
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import kotlinx.coroutines.flow.Flow

/**
 * Sinkron Beranda yang berjalan di latar belakang (foreground worker), jadi tidak terputus saat app dibuang
 * dari recents. Hasilnya disimpan sampai dibaca, supaya muncul sebagai dialog begitu app dibuka lagi.
 *
 * Tombol Sinkron, sinkron pertama, dan sinkron otomatis terjadwal memakai satu jalur yang sama; yang membedakan
 * hanya [SyncTrigger] (pacing, kantong jatah harian, dan jam tenang).
 */
interface HomeSyncJobs {
    /** True selama sinkron latar belakang (apa pun pemicunya) menunggu giliran atau sedang berjalan. */
    val running: Flow<Boolean>

    /** True selama yang berjalan / menunggu giliran adalah sinkron otomatis terjadwal. */
    val automaticRunning: Flow<Boolean>

    /** Hasil sinkron latar belakang yang belum dilihat user; null kalau tidak ada. */
    val pendingResult: Flow<HomeSyncResult?>

    /**
     * Mulai sinkron latar belakang. Kalau sudah ada yang berjalan, permintaan ini diabaikan, kecuali [SyncTrigger.MANUAL]
     * yang membatalkan sinkron otomatis dan menggantikannya (progres per halaman sudah tersimpan, jadi manual
     * melanjutkan dari cursor).
     */
    suspend fun start(trigger: SyncTrigger)

    /** Hasil sudah dilihat user. */
    suspend fun consumeResult()
}