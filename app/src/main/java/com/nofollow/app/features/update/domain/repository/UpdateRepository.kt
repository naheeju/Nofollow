package com.nofollow.app.features.update.domain.repository

import com.nofollow.app.core.common.AppResult
import com.nofollow.app.features.update.domain.model.AppUpdate
import com.nofollow.app.features.update.domain.model.AppVersion

interface UpdateRepository {
    /** Versi app yang sedang terpasang. */
    fun installedVersion(): AppVersion

    /**
     * Versi rilis terbaru di GitHub. Failure kalau tidak ada jaringan, repo belum publik, atau belum ada rilis;
     * pemanggil cukup mengabaikannya dan mencoba lagi nanti.
     */
    suspend fun fetchLatestVersion(): AppResult<AppVersion>

    /** Alamat unduh dan halaman rilis untuk [version], dipilihkan sesuai arsitektur HP ini. */
    fun updateFor(version: AppVersion): AppUpdate
}