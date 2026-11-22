package com.nofollow.app.features.tracker.data.repository

import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.local.FollowEventDao
import com.nofollow.app.features.tracker.data.local.FollowEventEntity
import com.nofollow.app.features.tracker.data.local.FollowEventType
import com.nofollow.app.features.tracker.data.local.FollowerDao
import com.nofollow.app.features.tracker.data.settings.FollowingStore
import com.nofollow.app.features.tracker.domain.model.AudienceSummary
import com.nofollow.app.features.tracker.domain.repository.AudienceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Empat angka kartu, semuanya dihitung dari data lokal (tanpa request):
 * - mutual = following yang juga follower
 * - fans = follower yang tidak ada di following
 * - tidak follow balik = following yang bukan follower
 * - unfollowers = event UNFOLLOW/UNAVAILABLE dalam 30 hari (dan 7 hari)
 */
class AudienceRepositoryImpl(
    private val followerDao: FollowerDao,
    private val eventDao: FollowEventDao,
    private val followingStore: FollowingStore,
    sessionStore: SessionStore,
) : AudienceRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val summary: Flow<AudienceSummary?> = sessionStore.session
        .map { it?.userId }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) {
                flowOf<AudienceSummary?>(null)
            } else {
                combine(
                    followerDao.observeActivePks(owner),
                    eventDao.observeRecent(owner, EVENT_WINDOW),
                    followingStore.snapshot,
                ) { pks, events, following ->
                    build(owner, pks, events, following)
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private fun build(
        owner: String,
        followerPks: List<String>,
        events: List<FollowEventEntity>,
        following: FollowingStore.Snapshot?,
    ): AudienceSummary {
        val now = System.currentTimeMillis()
        val followers = followerPks.toHashSet()

        val gone = events.filter { it.type == FollowEventType.UNFOLLOW || it.type == FollowEventType.UNAVAILABLE }
        val list = following?.takeIf { it.ownerId == owner }
        val mutual = list?.users?.count { it.pk in followers }

        return AudienceSummary(
            followers = followers.size,
            following = list?.users?.size,
            mutual = mutual,
            fans = mutual?.let { followers.size - it },
            notFollowingBack = if (list != null && mutual != null) list.users.size - mutual else null,
            unfollowers = gone.count { now - it.occurredAt <= MONTH_MS },
            unfollowersThisWeek = gone.count { now - it.occurredAt <= WEEK_MS },
            followingSyncedAt = list?.syncedAt,
            followingPks = list?.users?.mapTo(HashSet()) { it.pk },
        )
    }

    private companion object {
        const val EVENT_WINDOW = 500
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
    }
}