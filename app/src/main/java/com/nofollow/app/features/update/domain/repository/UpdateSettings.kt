package com.nofollow.app.features.update.domain.repository

import com.nofollow.app.features.update.domain.model.AppVersion
import kotlinx.coroutines.flow.Flow

/** Pilihan user dan status cek pembaruan yang perlu diingat antar sesi. */
interface UpdateSettings {
    /** Cek pembaruan aktif atau tidak. Default aktif. */
    val enabled: Flow<Boolean>

    /** Versi terbaru yang terakhir diketahui dari GitHub (bisa sama atau lebih lama dari yang terpasang). */
    val availableVersion: Flow<AppVersion?>

    /** Versi yang dialognya sudah ditutup user dengan "Nanti"; dialog tidak muncul lagi untuk versi ini. */
    val dismissedVersion: Flow<AppVersion?>

    /** Kapan terakhir mencoba cek (epoch ms), 0 kalau belum pernah. */
    suspend fun lastCheckedAt(): Long

    /** Versi yang notifikasinya sudah dikirim, supaya satu versi tidak diberitahukan berulang. */
    suspend fun notifiedVersion(): AppVersion?

    suspend fun setEnabled(on: Boolean)

    /** Catat percobaan cek pada [at]. [latest] null = cek gagal: hanya waktunya yang dicatat. */
    suspend fun recordCheck(at: Long, latest: AppVersion?)

    suspend fun setDismissed(version: AppVersion)

    suspend fun setNotified(version: AppVersion)
}