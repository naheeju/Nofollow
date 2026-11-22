package com.nofollow.app.features.kepo.domain.repository

import com.nofollow.app.features.kepo.domain.model.KepoData
import com.nofollow.app.features.kepo.domain.model.KepoOutcome
import kotlinx.coroutines.flow.Flow

interface KepoRepository {
    /** Semua penonton story yang tercatat (belum diurutkan; pakai KepoRanking) plus jumlah story yang tercatat. */
    fun observe(): Flow<KepoData>

    /**
     * Cek story dan penontonnya. [manual] = tombol "Cek sekarang" (jam tenang tidak berlaku, boleh lebih sering);
     * otomatis (worker / buka app) mengikuti jarak minimal di [com.nofollow.app.features.kepo.domain.model.KepoConfig].
     *
     * [full] = selalu ambil tray dan penonton tanpa menunggu jarak otomatis, seperti tombol manual, tapi jam tenang
     * dan kantong request otomatis tetap berlaku. Dipakai sinkron otomatis terjadwal.
     */
    suspend fun sync(manual: Boolean, full: Boolean = manual): KepoOutcome

    suspend fun markSeen()
}