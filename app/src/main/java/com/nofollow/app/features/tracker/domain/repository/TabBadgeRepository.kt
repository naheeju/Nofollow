package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.TabBadges
import com.nofollow.app.features.tracker.domain.model.TrackerTab
import kotlinx.coroutines.flow.Flow

interface TabBadgeRepository {
    /** Data baru per kategori, dihitung dari data lokal. Semua nol selama belum login atau following belum diambil. */
    val badges: Flow<TabBadges>

    /** Kategori [tab] sudah dilihat: lencananya hilang sampai ada data baru lagi. Kategori tanpa lencana diabaikan. */
    suspend fun markSeen(tab: TrackerTab)
}