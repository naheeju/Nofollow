package com.nofollow.app.features.tracker.data.repository

import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.local.FollowEventDao
import com.nofollow.app.features.tracker.data.local.FollowerDao
import com.nofollow.app.features.tracker.data.settings.FollowingStore
import com.nofollow.app.features.tracker.data.settings.TabSeenStore
import com.nofollow.app.features.tracker.domain.model.TabBadges
import com.nofollow.app.features.tracker.domain.model.TrackerTab
import com.nofollow.app.features.tracker.domain.repository.TabBadgeRepository
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
 * Lencana kecil di menu Beranda, semuanya dari data lokal (tanpa request): berapa data baru di tiap kategori
 * sejak kategori itu terakhir dilihat di Tracker.
 * - Unfollowers = kejadian UNFOLLOW/UNAVAILABLE dengan id di atas penanda.
 * - Tidak follow balik / Fans / Mutual = PK yang ada sekarang tapi tidak ada di daftar saat terakhir dilihat.
 * Terkumpul dari sinkron mana pun (tombol, buka app, worker, pantau cepat) dan hilang per kategori.
 */
class TabBadgeRepositoryImpl(
    private val followerDao: FollowerDao,
    private val eventDao: FollowEventDao,
    private val followingStore: FollowingStore,
    private val seenStore: TabSeenStore,
    private val sessionStore: SessionStore,
) : TabBadgeRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val badges: Flow<TabBadges> = sessionStore.session
        .map { it?.userId }
        .distinctUntilChanged()
        .flatMapLatest { owner -> if (owner == null) flowOf(TabBadges()) else badgesFor(owner) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    override suspend fun markSeen(tab: TrackerTab) {
        val owner = sessionStore.session.value?.userId ?: return
        when (tab) {
            TrackerTab.UNFOLLOWERS -> seenStore.markUnfollowSeen(owner, eventDao.maxId(owner) ?: 0L)
            TrackerTab.NOT_FOLLOWING_BACK, TrackerTab.FANS, TrackerTab.MUTUAL -> {
                val lists = derive(owner, followerDao.activePks(owner), followingStore.current()) ?: return
                seenStore.setPks(owner, tab, lists.getValue(tab))
            }
            else -> Unit
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun badgesFor(owner: String): Flow<TabBadges> {
        val unfollowers = seenStore.seen(owner)
            .map { it.unfollowId }
            .distinctUntilChanged()
            .flatMapLatest { seenId ->
                if (seenId == null) {
                    // Pemakaian pertama: yang sudah ada dianggap dilihat. Setelah ditulis, seenId emit lagi.
                    seenStore.initUnfollowIfAbsent(owner, eventDao.maxId(owner) ?: 0L)
                    flowOf(0)
                } else {
                    eventDao.observeGoneCountAfter(owner, seenId)
                }
            }

        val lists = combine(
            followerDao.observeActivePks(owner),
            followingStore.snapshot,
            seenStore.seen(owner),
        ) { followerPks, following, seen ->
            val current = derive(owner, followerPks, following) ?: return@combine TabBadges()
            // Kategori yang belum punya penanda dianggap sudah dilihat; setelah ditulis, seen emit lagi.
            val uninitialized = current.filterKeys { it !in seen.pks }
            if (uninitialized.isNotEmpty()) seenStore.initPksIfAbsent(owner, uninitialized)
            fun fresh(tab: TrackerTab): Int = seen.pks[tab]?.let { old -> current.getValue(tab).count { it !in old } } ?: 0
            TabBadges(
                notFollowingBack = fresh(TrackerTab.NOT_FOLLOWING_BACK),
                fans = fresh(TrackerTab.FANS),
                mutual = fresh(TrackerTab.MUTUAL),
            )
        }

        return combine(unfollowers, lists) { gone, derived -> derived.copy(unfollowers = gone) }
    }

    /**
     * PK per kategori turunan, atau null selama daftar following belum pernah diambil.
     * Rumusnya sama dengan AudienceRepositoryImpl: mutual = following yang juga follower, fans = follower yang tidak
     * di-follow, tidak follow balik = following yang bukan follower.
     */
    private fun derive(
        owner: String,
        followerPks: List<String>,
        following: FollowingStore.Snapshot?,
    ): Map<TrackerTab, Set<String>>? {
        val list = following?.takeIf { it.ownerId == owner } ?: return null
        val followers = followerPks.toHashSet()
        val followingPks = list.users.mapTo(HashSet()) { it.pk }
        return mapOf(
            TrackerTab.NOT_FOLLOWING_BACK to followingPks.filterTo(HashSet()) { it !in followers },
            TrackerTab.FANS to followers.filterTo(HashSet()) { it !in followingPks },
            TrackerTab.MUTUAL to followers.filterTo(HashSet()) { it in followingPks },
        )
    }
}