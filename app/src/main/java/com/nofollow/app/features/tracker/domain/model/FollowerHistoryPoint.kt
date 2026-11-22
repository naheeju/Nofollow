package com.nofollow.app.features.tracker.domain.model

/** Jumlah follower pada satu hari. [day] = nomor hari lokal (zona waktu perangkat). */
data class FollowerHistoryPoint(
    val day: Long,
    val followers: Int,
)