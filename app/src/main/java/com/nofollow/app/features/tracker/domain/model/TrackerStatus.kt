package com.nofollow.app.features.tracker.domain.model

enum class EventKind { UNFOLLOW, FOLLOW, REFOLLOW, RENAME, UNAVAILABLE }

data class FollowEventItem(
    val id: Long,
    /** ID numerik akun; tetap walau username berganti. */
    val pk: String,
    val kind: EventKind,
    val username: String,
    val previousUsername: String?,
    /** Terakhir terlihat sebagai follower (hanya UNFOLLOW/UNAVAILABLE). */
    val lastSeenAt: Long?,
    /** Waktu terdeteksi. */
    val occurredAt: Long,
    /** Path file foto akun ini; null kalau belum ada. */
    val avatarPath: String? = null,
)

/** Ringkasan untuk layar utama. */
data class TrackerStatus(
    val mode: SyncMode,
    val activeFollowers: Int,
    val requestsToday: Int,
    val dailyCap: Int,
    val baselineDone: Boolean,
    val sweepInProgress: Boolean,
    val sweepPagesDone: Int,
    val lastSweepAt: Long?,
    val lastSweepDurationMs: Long?,
    val nextSweepAt: Long?,
    val penaltyUntil: Long,
    val cooldownUntil: Long,
    val throttleCount: Int,
    val haltReason: String?,
    val events: List<FollowEventItem>,
)