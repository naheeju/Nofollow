package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.AudienceLists
import com.nofollow.app.features.tracker.domain.model.NotFollowingBackEntry
import com.nofollow.app.features.tracker.domain.model.TrackerLogEntry
import kotlinx.coroutines.flow.Flow

interface TrackerLogRepository {
    /** Kejadian terbaru dulu (maks beberapa ratus). Kosong selama belum login. */
    val entries: Flow<List<TrackerLogEntry>>

    /**
     * Following yang tidak follow balik, dihitung lokal dari daftar following tersimpan dan follower aktif.
     * Null selama daftar following belum pernah diambil.
     */
    val notFollowingBack: Flow<List<NotFollowingBackEntry>?>

    /**
     * Fans dan mutual, dihitung lokal dari follower aktif dan daftar following tersimpan.
     * Null selama daftar following belum pernah diambil.
     */
    val audienceLists: Flow<AudienceLists?>

    /** Jumlah kejadian baru sejak Tracker terakhir dilihat; sumber lencana. 0 kalau belum login. */
    val unseenCount: Flow<Int>

    /** Kunci ([TrackerLogEntry.checkKey]) kejadian yang sudah ditandai dicek oleh user. */
    val checkedKeys: Flow<Set<String>>

    suspend fun setChecked(key: String, checked: Boolean)

    /** Tandai semua kejadian sampai sekarang sudah dilihat (lencana Tracker hilang). */
    suspend fun markSeen()
}