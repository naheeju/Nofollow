package com.nofollow.app.di

import com.nofollow.app.features.home.data.notify.HomeSyncNotifier
import com.nofollow.app.features.home.data.sync.HomeSyncJobsImpl
import com.nofollow.app.features.home.data.sync.HomeSyncResultStore
import com.nofollow.app.features.home.domain.repository.HomeSyncJobs
import com.nofollow.app.features.home.domain.usecase.ObserveHomeOverviewUseCase
import com.nofollow.app.features.home.domain.usecase.OnHomeOpenedUseCase
import com.nofollow.app.features.home.domain.usecase.SyncAllUseCase
import com.nofollow.app.features.home.ui.HomeViewModel
import com.nofollow.app.features.tracker.data.work.AutoSyncScheduler
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeModule = module {
    single { HomeSyncResultStore(androidContext()) }
    single { HomeSyncNotifier(androidContext()) }
    single<HomeSyncJobs> { HomeSyncJobsImpl(androidContext(), get()) }
    // Sinkron otomatis: alarm yang bangun lalu menjalankan worker foreground yang sama dengan tombol Sinkron.
    single {
        AutoSyncScheduler(androidContext(), get(), get(), get(), get()) {
            get<HomeSyncJobs>().start(SyncTrigger.SCHEDULED)
        }
    }
    factoryOf(::ObserveHomeOverviewUseCase)
    factoryOf(::SyncAllUseCase)
    factoryOf(::OnHomeOpenedUseCase)
    viewModelOf(::HomeViewModel)
}