package com.nofollow.app.features.tracker.data.repository

import com.nofollow.app.core.common.localDayNumber
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.avatar.AvatarStore
import com.nofollow.app.features.tracker.data.local.DailyUsageDao
import com.nofollow.app.features.tracker.data.local.FollowEventDao
import com.nofollow.app.features.tracker.data.local.FollowerDao
import com.nofollow.app.features.tracker.data.local.SyncStateDao
import com.nofollow.app.features.tracker.data.settings.SyncSettings
import com.nofollow.app.features.tracker.domain.model.RequestBucket
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.data.sync.SyncPlanner
import com.nofollow.app.features.tracker.domain.model.EventKind
import com.nofollow.app.features.tracker.domain.model.FollowEventItem
import com.nofollow.app.features.tracker.domain.model.SyncMode
import com.nofollow.app.features.tracker.domain.model.TrackerStatus
import com.nofollow.app.features.tracker.domain.repository.TrackerStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

class TrackerStatusRepositoryImpl(
    private val sessionStore: SessionStore,
    private val followerDao: FollowerDao,
    private val eventDao: FollowEventDao,
    private val syncDao: SyncStateDao,
    private val usageDao: DailyUsageDao,
    private val settings: SyncSettings,
    private val config: SyncConfig,
    private val avatars: AvatarStore,
) : TrackerStatusRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStatus(): Flow<TrackerStatus?> =
        sessionStore.session
            .map { it?.userId }
            .distinctUntilChanged()
            .flatMapLatest { owner ->
                if (owner == null) flowOf<TrackerStatus?>(null) else statusFor(owner)
            }

    override suspend fun setMode(mode: SyncMode) {
        settings.setMode(mode)
        // Jadwal sweep berikutnya dihitung ulang dari sweep terakhir dengan interval mode baru.
        val owner = sessionStore.session.value?.userId ?: return
        val state = syncDao.get(owner) ?: return
        val last = state.lastCompletedSweepAt ?: return
        val now = System.currentTimeMillis()
        syncDao.setNextSweepAt(owner, SyncPlanner.nextSweepAt(last, mode, now < state.penaltyUntil, config))
    }

    override suspend fun markEventsRead() {
        val owner = sessionStore.session.value?.userId ?: return
        eventDao.markAllNotified(owner)
        // Notifikasi sengaja tidak dibatalkan di sini: dibiarkan sampai user menggesernya sendiri,
        // supaya tidak hilang sebelum sempat dibaca. Riwayat tetap ada di layar.
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun statusFor(owner: String): Flow<TrackerStatus> {
        // Semua kantong plafon dijumlah untuk tampilan.
        val usage = dayTicker().flatMapLatest { day ->
            combine(
                RequestBucket.entries.map { usageDao.observe(config.usageKey(owner, it), day) },
            ) { counts -> counts.sumOf { it ?: 0 } }
        }
        return combine(
            syncDao.observe(owner),
            followerDao.observeActiveCount(owner),
            eventDao.observeRecent(owner, EVENT_LIMIT),
            usage,
            settings.mode,
        ) { sync, active, events, used, mode ->
            TrackerStatus(
                mode = mode,
                activeFollowers = active,
                requestsToday = used,
                dailyCap = config.totalDailyCap,
                baselineDone = sync?.baselineDone ?: false,
                sweepInProgress = sync?.sweepInProgress ?: false,
                sweepPagesDone = sync?.sweepPagesDone ?: 0,
                lastSweepAt = sync?.lastCompletedSweepAt,
                lastSweepDurationMs = sync?.lastSweepDurationMs,
                nextSweepAt = sync?.nextSweepAt,
                penaltyUntil = sync?.penaltyUntil ?: 0,
                cooldownUntil = sync?.cooldownUntil ?: 0,
                throttleCount = sync?.throttleCount ?: 0,
                haltReason = sync?.haltReason,
                events = events.map {
                    FollowEventItem(
                        id = it.id,
                        pk = it.pk,
                        kind = EventKind.valueOf(it.type.name),
                        username = it.username,
                        previousUsername = it.previousUsername,
                        lastSeenAt = it.lastSeenAt,
                        occurredAt = it.occurredAt,
                        avatarPath = avatars.pathOrNull(it.pk),
                    )
                },
            )
        }.flowOn(Dispatchers.IO) // pathOrNull menyentuh disk
    }

    /** Emit nomor hari lokal; berubah saat lewat tengah malam supaya penghitung "hari ini" ikut reset. */
    private fun dayTicker(): Flow<Long> = flow {
        while (true) {
            emit(localDayNumber(System.currentTimeMillis()))
            delay(DAY_CHECK_MS.milliseconds)
        }
    }.distinctUntilChanged()

    private companion object {
        const val EVENT_LIMIT = 100
        const val DAY_CHECK_MS = 60_000L
    }
}