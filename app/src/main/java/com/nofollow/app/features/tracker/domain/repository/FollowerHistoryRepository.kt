package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.FollowerHistoryPoint
import kotlinx.coroutines.flow.Flow

interface FollowerHistoryRepository {
    /** Riwayat [days] hari terakhir, urut dari yang paling lama. Kosong selama belum login atau belum ada data. */
    fun observe(days: Int): Flow<List<FollowerHistoryPoint>>
}