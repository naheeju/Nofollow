package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.AccountProfile
import kotlinx.coroutines.flow.Flow

interface AccountProfileRepository {
    /** Null selama belum login, atau username akun ini belum pernah berhasil diambil. */
    val profile: Flow<AccountProfile?>

    /**
     * Ambil username dan foto kalau belum ada atau sudah kedaluwarsa (14 hari). Aman dipanggil berulang:
     * kalau tidak ada yang perlu diambil, tidak ada request ke Instagram.
     */
    suspend fun refresh()
}