package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.SyncMode
import com.nofollow.app.features.tracker.domain.model.TrackerStatus
import kotlinx.coroutines.flow.Flow

interface TrackerStatusRepository {
    /** Null kalau belum login. */
    fun observeStatus(): Flow<TrackerStatus?>

    suspend fun setMode(mode: SyncMode)

    /** Tandai semua unfollow sebagai sudah dibaca dan hapus notifikasinya. */
    suspend fun markEventsRead()
}