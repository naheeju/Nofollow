package com.nofollow.app.features.tracker.data.sync

import com.nofollow.app.features.tracker.data.local.FollowEventEntity
import com.nofollow.app.features.tracker.data.local.FollowEventType
import com.nofollow.app.features.tracker.data.local.FollowerEntity

/** Satu follower hasil parse dari halaman API. */
data class RemoteFollower(
    val pk: String,
    val username: String,
    val fullName: String,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val picUrl: String? = null,
)

data class PageDiff(
    val upserts: List<FollowerEntity>,
    val events: List<FollowEventEntity>,
)

object FollowerDiff {
    /**
     * Membandingkan satu halaman dengan isi database.
     * Unfollow TIDAK dihitung di sini: itu baru diketahui setelah satu sweep penuh + konfirmasi.
     *
     * @param baselineDone false = sweep pertama, semua akun masuk diam-diam tanpa event FOLLOW.
     */
    fun diffPage(
        ownerId: String,
        page: List<RemoteFollower>,
        existing: Map<String, FollowerEntity>,
        sweepId: Long,
        baselineDone: Boolean,
        now: Long,
    ): PageDiff {
        val upserts = LinkedHashMap<String, FollowerEntity>()
        val events = ArrayList<FollowEventEntity>()

        for (f in page) {
            // Kalau pk yang sama muncul dua kali (daftar bergeser saat sweep), pakai versi di memori.
            val prev = upserts[f.pk] ?: existing[f.pk]
            val row = FollowerEntity(
                ownerId = ownerId,
                pk = f.pk,
                username = f.username,
                fullName = f.fullName,
                isPrivate = f.isPrivate,
                isVerified = f.isVerified,
                isActive = true,
                firstSeenAt = prev?.firstSeenAt ?: now,
                lastSeenAt = now,
                lastSeenSweepId = sweepId,
                goneAt = null,
                // Kalau halaman ini tidak membawa URL, pertahankan yang lama.
                picUrl = f.picUrl ?: prev?.picUrl,
            )
            when {
                prev == null -> if (baselineDone) events += event(ownerId, f, FollowEventType.FOLLOW, now)
                !prev.isActive -> events += event(ownerId, f, FollowEventType.REFOLLOW, now)
                prev.username != f.username ->
                    events += event(ownerId, f, FollowEventType.RENAME, now, previous = prev.username)
            }
            upserts[f.pk] = row
        }
        return PageDiff(upserts.values.toList(), events)
    }

    private fun event(
        ownerId: String,
        f: RemoteFollower,
        type: FollowEventType,
        now: Long,
        previous: String? = null,
    ) = FollowEventEntity(
        ownerId = ownerId,
        pk = f.pk,
        type = type,
        username = f.username,
        previousUsername = previous,
        occurredAt = now,
    )
}