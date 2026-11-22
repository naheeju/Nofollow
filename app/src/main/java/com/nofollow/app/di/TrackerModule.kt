package com.nofollow.app.di

import com.nofollow.app.features.tracker.data.repository.TabBadgeRepositoryImpl
import com.nofollow.app.features.tracker.data.repository.TrackerLogRepositoryImpl
import com.nofollow.app.features.tracker.data.settings.TabSeenStore
import com.nofollow.app.features.tracker.domain.repository.TabBadgeRepository
import com.nofollow.app.features.tracker.domain.usecase.MarkTabSeenUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTabBadgesUseCase
import com.nofollow.app.features.tracker.data.settings.CheckedEntriesStore
import com.nofollow.app.features.tracker.data.settings.TrackerSeenStore
import com.nofollow.app.features.tracker.domain.repository.TrackerLogRepository
import com.nofollow.app.features.tracker.domain.usecase.MarkTrackerSeenUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveAudienceListsUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveCheckedEntriesUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveUnseenTrackerCountUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveNotFollowingBackUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTrackerLogUseCase
import com.nofollow.app.features.tracker.domain.usecase.SetEntryCheckedUseCase
import com.nofollow.app.features.tracker.ui.TrackerLogViewModel
import androidx.room.Room
import com.nofollow.app.features.tracker.data.avatar.AvatarStore
import com.nofollow.app.features.tracker.data.local.TrackerDatabase
import com.nofollow.app.features.tracker.data.local.TrackerMigrations
import com.nofollow.app.features.tracker.data.notify.UnfollowNotifier
import com.nofollow.app.features.setting.ui.SettingsViewModel
import com.nofollow.app.features.tracker.data.probe.CountEndpointTester
import com.nofollow.app.features.tracker.data.probe.StoryViewersTester
import com.nofollow.app.features.tracker.data.probe.ProbeControlImpl
import com.nofollow.app.features.tracker.domain.probe.CountEndpointProbe
import com.nofollow.app.features.tracker.domain.probe.StoryViewersProbe
import com.nofollow.app.features.tracker.data.remote.InstagramApi
import com.nofollow.app.features.tracker.data.remote.WebViewFetcher
import com.nofollow.app.features.tracker.data.repository.AccountProfileRepositoryImpl
import com.nofollow.app.features.tracker.data.repository.AudienceRepositoryImpl
import com.nofollow.app.features.tracker.data.repository.FollowerHistoryRepositoryImpl
import com.nofollow.app.features.tracker.data.repository.FollowingSyncRepositoryImpl
import com.nofollow.app.features.tracker.data.repository.FollowerSyncRepositoryImpl
import com.nofollow.app.features.tracker.data.repository.TrackerStatusRepositoryImpl
import com.nofollow.app.features.tracker.data.settings.AccountProfileStore
import com.nofollow.app.features.tracker.data.settings.FollowingStore
import com.nofollow.app.features.tracker.data.settings.SyncSettings
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.data.sync.SyncGate
import com.nofollow.app.features.tracker.domain.repository.AccountProfileRepository
import com.nofollow.app.features.tracker.domain.repository.AudienceRepository
import com.nofollow.app.features.tracker.domain.repository.FollowerHistoryRepository
import com.nofollow.app.features.tracker.domain.repository.FollowingSyncRepository
import com.nofollow.app.features.tracker.domain.repository.FollowerSyncRepository
import com.nofollow.app.features.tracker.domain.repository.ProbeControl
import com.nofollow.app.features.tracker.domain.repository.TrackerStatusRepository
import com.nofollow.app.features.tracker.domain.usecase.EnsureProbeRunningUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveAudienceSummaryUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveFollowerHistoryUseCase
import com.nofollow.app.features.tracker.domain.usecase.SyncFollowingUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveAccountProfileUseCase
import com.nofollow.app.features.tracker.domain.usecase.RefreshAccountProfileUseCase
import com.nofollow.app.features.tracker.domain.usecase.MarkEventsReadUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveProbeEnabledUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTrackerStatusUseCase
import com.nofollow.app.features.tracker.domain.usecase.ProbeFollowersUseCase
import com.nofollow.app.features.tracker.domain.usecase.SetProbeEnabledUseCase
import com.nofollow.app.features.tracker.domain.usecase.SetSyncModeUseCase
import com.nofollow.app.features.tracker.domain.usecase.SyncFollowersUseCase
import com.nofollow.app.features.tracker.domain.usecase.TestCountEndpointUseCase
import com.nofollow.app.features.tracker.domain.usecase.TestStoryViewersUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val trackerModule = module {
    single {
        Room.databaseBuilder(androidContext(), TrackerDatabase::class.java, "nofollow.db")
            .addMigrations(TrackerMigrations.MIGRATION_3_4, TrackerMigrations.MIGRATION_4_5, TrackerMigrations.MIGRATION_5_6, TrackerMigrations.MIGRATION_6_7)
            // Hanya versi lama sebelum migrasi eksplisit ada yang boleh dibuat ulang. Versi >= 3 wajib lewat Migration.
            .fallbackToDestructiveMigrationFrom(true, 1, 2)
            .build()
    }
    single { get<TrackerDatabase>().followerDao() }
    single { get<TrackerDatabase>().eventDao() }
    single { get<TrackerDatabase>().syncStateDao() }
    single { get<TrackerDatabase>().usageDao() }
    single { get<TrackerDatabase>().historyDao() }

    single { WebViewFetcher(androidContext(), get()) }
    single { InstagramApi(get()) }
    single { SyncConfig() }
    single { SyncSettings(androidContext()) }
    single { UnfollowNotifier(androidContext(), get(), get()) }
    single { SyncGate() }
    single<ProbeControl> { ProbeControlImpl(get(), get()) }
    single { AccountProfileStore(androidContext()) }
    single<CountEndpointProbe> { CountEndpointTester(get(), get()) }
    single<StoryViewersProbe> { StoryViewersTester(get(), get()) }
    single { FollowingStore(androidContext()) }
    single { CheckedEntriesStore(androidContext()) }
    single { TrackerSeenStore(androidContext()) }
    single { TabSeenStore(androidContext()) }
    single { AvatarStore(androidContext(), get()) }
    single<AccountProfileRepository> {
        AccountProfileRepositoryImpl(androidContext(), get(), get(), get(), get(), get())
    }

    singleOf(::FollowerSyncRepositoryImpl) bind FollowerSyncRepository::class
    singleOf(::TrackerStatusRepositoryImpl) bind TrackerStatusRepository::class
    singleOf(::FollowingSyncRepositoryImpl) bind FollowingSyncRepository::class
    singleOf(::AudienceRepositoryImpl) bind AudienceRepository::class
    singleOf(::FollowerHistoryRepositoryImpl) bind FollowerHistoryRepository::class
    singleOf(::TrackerLogRepositoryImpl) bind TrackerLogRepository::class
    singleOf(::TabBadgeRepositoryImpl) bind TabBadgeRepository::class

    factoryOf(::SyncFollowersUseCase)
    factoryOf(::ObserveTrackerStatusUseCase)
    factoryOf(::SetSyncModeUseCase)
    factoryOf(::MarkEventsReadUseCase)
    factoryOf(::ProbeFollowersUseCase)
    factoryOf(::ObserveProbeEnabledUseCase)
    factoryOf(::SetProbeEnabledUseCase)
    factoryOf(::EnsureProbeRunningUseCase)
    factoryOf(::ObserveAccountProfileUseCase)
    factoryOf(::RefreshAccountProfileUseCase)
    factoryOf(::ObserveAudienceSummaryUseCase)
    factoryOf(::SyncFollowingUseCase)
    factoryOf(::ObserveFollowerHistoryUseCase)
    factoryOf(::ObserveTrackerLogUseCase)
    factoryOf(::ObserveCheckedEntriesUseCase)
    factoryOf(::ObserveNotFollowingBackUseCase)
    factoryOf(::SetEntryCheckedUseCase)
    factoryOf(::ObserveAudienceListsUseCase)
    factoryOf(::ObserveUnseenTrackerCountUseCase)
    factoryOf(::MarkTrackerSeenUseCase)
    factoryOf(::ObserveTabBadgesUseCase)
    factoryOf(::MarkTabSeenUseCase)
    factoryOf(::TestCountEndpointUseCase)
    factoryOf(::TestStoryViewersUseCase)
    viewModelOf(::TrackerLogViewModel)
    viewModelOf(::SettingsViewModel)
}