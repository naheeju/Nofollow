package com.nofollow.app.features.tracker.domain.repository

import kotlinx.coroutines.flow.Flow

/** Saklar "pantau cepat": foreground service yang mengecek jumlah follower tiap beberapa menit. */
interface ProbeControl {
    val enabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)

    /** Dipanggil saat app tampil: kalau fitur aktif, pastikan service hidup. */
    suspend fun ensureRunning()
}