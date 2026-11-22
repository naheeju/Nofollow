package com.nofollow.app.features.tracker.data.repository

import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.avatar.AvatarStore
import com.nofollow.app.features.tracker.data.local.FollowEventDao
import com.nofollow.app.features.tracker.data.local.FollowEventEntity
import com.nofollow.app.features.tracker.data.local.FollowerDao
import com.nofollow.app.features.tracker.data.local.FollowerEntity
import com.nofollow.app.features.tracker.data.settings.CheckedEntriesStore
import com.nofollow.app.features.tracker.data.settings.FollowingStore
import com.nofollow.app.features.tracker.data.settings.TrackerSeenStore
import com.nofollow.app.features.tracker.domain.model.AudienceEntry
import com.nofollow.app.features.tracker.domain.model.AudienceLists
import com.nofollow.app.features.tracker.domain.model.EventKind
import com.nofollow.app.features.tracker.domain.model.NotFollowingBackEntry
import com.nofollow.app.features.tracker.domain.model.TrackerLogEntry
import com.nofollow.app.features.tracker.domain.repository.TrackerLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Daftar kejadian untuk layar Tracker, semuanya dari data lokal (tanpa request):
 * event + nama tampilan (tabel follower) + apakah kamu mengikuti akunnya (daftar following) + foto.
 */
class TrackerLogRepositoryImpl(
    private val eventDao: FollowEventDao,
    private val followerDao: FollowerDao,
    private val followingStore: FollowingStore,
    private val avatars: AvatarStore,
    private val checked: CheckedEntriesStore,
    private val seen: TrackerSeenStore,
    private val sessionStore: SessionStore,
) : TrackerLogRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val entries: Flow<List<TrackerLogEntry>> = sessionStore.session
        .map { it?.userId }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) {
                flowOf(emptyList())
            } else {
                combine(eventDao.observeRecent(owner, LOG_LIMIT), followingStore.snapshot) { events, following ->
                    events to following
                }.map { (events, following) -> build(owner, events, following) }
            }
        }
        .flowOn(Dispatchers.IO) // findByPks dan pathOrNull menyentuh disk

    @OptIn(ExperimentalCoroutinesApi::class)
    override val notFollowingBack: Flow<List<NotFollowingBackEntry>?> = sessionStore.session
        .map { it?.userId }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) {
                flowOf<List<NotFollowingBackEntry>?>(null)
            } else {
                combine(followerDao.observeActivePks(owner), followingStore.snapshot) { pks, following ->
                    val list = following?.takeIf { it.ownerId == owner } ?: return@combine null
                    val followers = pks.toHashSet()
                    list.users
                        .filter { it.pk !in followers }
                        .map { NotFollowingBackEntry(it.pk, it.username, it.fullName, avatars.pathOrNull(it.pk)) }
                }
            }
        }
        .flowOn(Dispatchers.IO) // pathOrNull menyentuh disk

    @OptIn(ExperimentalCoroutinesApi::class)
    override val audienceLists: Flow<AudienceLists?> = sessionStore.session
        .map { it?.userId }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) {
                flowOf<AudienceLists?>(null)
            } else {
                combine(followerDao.observeActive(owner), followingStore.snapshot) { followers, following ->
                    val list = following?.takeIf { it.ownerId == owner } ?: return@combine null
                    val followingPks = list.users.mapTo(HashSet()) { it.pk }
                    val (mutual, fans) = followers.partition { it.pk in followingPks }
                    AudienceLists(
                        fans = fans.map { it.toEntry() },
                        mutual = mutual.map { it.toEntry() },
                    )
                }
            }
        }
        .flowOn(Dispatchers.IO) // pathOrNull menyentuh disk

    @OptIn(ExperimentalCoroutinesApi::class)
    override val unseenCount: Flow<Int> = sessionStore.session
        .map { it?.userId }
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) {
                flowOf(0)
            } else {
                seen.seenId(owner).flatMapLatest { seenId ->
                    if (seenId == null) {
                        // Pemakaian pertama: semua yang sudah ada dianggap dilihat. Setelah ditulis, seenId emit lagi.
                        seen.initIfAbsent(owner, eventDao.maxId(owner) ?: 0L)
                        flowOf(0)
                    } else {
                        eventDao.observeCountAfter(owner, seenId)
                    }
                }
            }
        }
        .distinctUntilChanged()

    override val checkedKeys: Flow<Set<String>> = checked.keys

    override suspend fun markSeen() {
        val owner = sessionStore.session.first()?.userId ?: return
        seen.markSeen(owner, eventDao.maxId(owner) ?: 0L)
    }

    override suspend fun setChecked(key: String, checked: Boolean) = this.checked.set(key, checked)

    private fun FollowerEntity.toEntry() = AudienceEntry(pk, username, fullName, avatars.pathOrNull(pk))

    private suspend fun build(
        owner: String,
        events: List<FollowEventEntity>,
        following: FollowingStore.Snapshot?,
    ): List<TrackerLogEntry> {
        val names = followerDao.findByPks(owner, events.map { it.pk }.distinct()).associate { it.pk to it.fullName }
        val followingPks = following?.takeIf { it.ownerId == owner }?.users?.mapTo(HashSet()) { it.pk }
        return events.map {
            TrackerLogEntry(
                id = it.id,
                pk = it.pk,
                kind = EventKind.valueOf(it.type.name),
                username = it.username,
                fullName = names[it.pk].orEmpty(),
                previousUsername = it.previousUsername,
                occurredAt = it.occurredAt,
                avatarPath = avatars.pathOrNull(it.pk),
                iFollow = followingPks?.let { set -> it.pk in set },
            )
        }
    }

    private companion object {
        /** Sama dengan jendela event di AudienceRepositoryImpl; di bawah batas 999 variabel SQLite untuk findByPks. */
        const val LOG_LIMIT = 500
    }
}