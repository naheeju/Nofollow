package com.nofollow.app.features.home.domain.usecase

import com.nofollow.app.features.home.domain.model.HomeActivity
import com.nofollow.app.features.home.domain.model.HomeActivityType
import com.nofollow.app.features.home.domain.model.GrowthPoint
import com.nofollow.app.features.home.domain.model.HomeAudience
import com.nofollow.app.features.home.domain.model.HomeGrowth
import com.nofollow.app.features.home.domain.model.HomeMenuBadges
import com.nofollow.app.features.home.domain.model.HomeOverview
import com.nofollow.app.features.home.domain.model.HomeProfile
import com.nofollow.app.features.home.domain.model.HomeSyncProgress
import com.nofollow.app.features.tracker.domain.model.AccountProfile
import com.nofollow.app.features.tracker.domain.model.AudienceSummary
import com.nofollow.app.features.tracker.domain.model.EventKind
import com.nofollow.app.features.tracker.domain.model.FollowEventItem
import com.nofollow.app.features.tracker.domain.model.FollowerHistoryPoint
import com.nofollow.app.features.tracker.domain.model.TabBadges
import com.nofollow.app.features.tracker.domain.usecase.ObserveAccountProfileUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveAudienceSummaryUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveFollowerHistoryUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTabBadgesUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTrackerStatusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Menggabungkan profil, empat angka, lencana menu, dan waktu sinkron dari feature tracker menjadi satu aliran untuk Beranda. */
class ObserveHomeOverviewUseCase(
    private val observeProfile: ObserveAccountProfileUseCase,
    private val observeAudience: ObserveAudienceSummaryUseCase,
    private val observeStatus: ObserveTrackerStatusUseCase,
    private val observeHistory: ObserveFollowerHistoryUseCase,
    private val observeBadges: ObserveTabBadgesUseCase,
) {
    operator fun invoke(): Flow<HomeOverview> =
        combine(
            observeProfile(),
            observeAudience(),
            observeStatus(),
            observeHistory(GROWTH_DAYS),
            observeBadges(),
        ) { profile, audience, status, history, badges ->
            HomeOverview(
                profile = profile?.toHome(),
                audience = audience?.toHome(),
                lastSyncedAt = listOfNotNull(status?.lastSweepAt, audience?.followingSyncedAt).maxOrNull(),
                recentActivity = status?.events.orEmpty().take(HomeOverview.ACTIVITY_LIMIT).map { it.toHome(audience?.followingPks) },
                growth = history.toGrowth(),
                syncProgress = status?.let {
                    HomeSyncProgress(
                        baselineDone = it.baselineDone,
                        sweepActive = it.sweepInProgress,
                        pagesDone = it.sweepPagesDone,
                        followersRead = it.activeFollowers,
                        followingCount = audience?.following,
                    )
                },
                menuBadges = badges.toHome(),
            )
        }.distinctUntilChanged()

    private fun List<FollowerHistoryPoint>.toGrowth(): HomeGrowth? {
        val first = firstOrNull() ?: return null
        val last = last()
        val delta = last.followers - first.followers
        return HomeGrowth(
            points = map { GrowthPoint(it.day, it.followers) },
            current = last.followers,
            delta = delta,
            deltaPercent = first.followers.takeIf { it > 0 }?.let { delta * 100f / it },
            spanDays = (last.day - first.day).toInt(),
        )
    }

    private fun FollowEventItem.toHome(followingPks: Set<String>?) = HomeActivity(
        id = id,
        pk = pk,
        type = when (kind) {
            EventKind.UNFOLLOW -> HomeActivityType.UNFOLLOW
            EventKind.FOLLOW -> HomeActivityType.FOLLOW
            EventKind.REFOLLOW -> HomeActivityType.REFOLLOW
            EventKind.RENAME -> HomeActivityType.RENAME
            EventKind.UNAVAILABLE -> HomeActivityType.UNAVAILABLE
        },
        username = username,
        previousUsername = previousUsername,
        occurredAt = occurredAt,
        avatarPath = avatarPath,
        iFollow = followingPks?.let { pk in it },
    )

    private fun TabBadges.toHome() = HomeMenuBadges(
        notFollowingBack = notFollowingBack,
        unfollowers = unfollowers,
        fans = fans,
        mutual = mutual,
    )

    private fun AccountProfile.toHome() = HomeProfile(username, avatarPath, avatarVersion)

    private fun AudienceSummary.toHome() = HomeAudience(
        notFollowingBack = notFollowingBack,
        unfollowers = unfollowers,
        unfollowersThisWeek = unfollowersThisWeek,
        fans = fans,
        mutual = mutual,
        mutualPercent = mutualPercent(),
        followingSyncedAt = followingSyncedAt,
    )

    private fun AudienceSummary.mutualPercent(): Float? {
        val mutual = mutual ?: return null
        val following = following?.takeIf { it > 0 } ?: return null
        return mutual * 100f / following
    }

    private companion object {
        /** Jendela grafik pertumbuhan. */
        const val GROWTH_DAYS = 30
    }
}