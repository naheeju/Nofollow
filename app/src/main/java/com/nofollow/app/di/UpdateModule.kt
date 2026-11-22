package com.nofollow.app.di

import com.nofollow.app.features.update.data.notify.UpdateNotifier
import com.nofollow.app.features.update.data.remote.GitHubReleaseApi
import com.nofollow.app.features.update.data.repository.UpdateRepositoryImpl
import com.nofollow.app.features.update.data.settings.UpdateSettingsStore
import com.nofollow.app.features.update.data.work.UpdateCheckScheduler
import com.nofollow.app.features.update.domain.model.UpdateConfig
import com.nofollow.app.features.update.domain.repository.UpdateRepository
import com.nofollow.app.features.update.domain.repository.UpdateSettings
import com.nofollow.app.features.update.domain.usecase.CheckForUpdateUseCase
import com.nofollow.app.features.update.domain.usecase.ClaimUpdateNotificationUseCase
import com.nofollow.app.features.update.domain.usecase.DismissUpdatePromptUseCase
import com.nofollow.app.features.update.domain.usecase.ObserveAvailableUpdateUseCase
import com.nofollow.app.features.update.domain.usecase.ObserveUpdateEnabledUseCase
import com.nofollow.app.features.update.domain.usecase.ObserveUpdatePromptUseCase
import com.nofollow.app.features.update.domain.usecase.SetUpdateEnabledUseCase
import com.nofollow.app.features.update.ui.UpdateViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val updateModule = module {
    single { UpdateConfig() }
    single<UpdateSettings> { UpdateSettingsStore(androidContext()) }
    single { GitHubReleaseApi(get(), get()) }
    single<UpdateRepository> { UpdateRepositoryImpl(androidContext(), get(), get()) }
    single { UpdateNotifier(androidContext()) }
    single { UpdateCheckScheduler(androidContext(), get()) }

    factoryOf(::CheckForUpdateUseCase)
    factoryOf(::ObserveAvailableUpdateUseCase)
    factoryOf(::ObserveUpdatePromptUseCase)
    factoryOf(::DismissUpdatePromptUseCase)
    factoryOf(::ClaimUpdateNotificationUseCase)
    factoryOf(::ObserveUpdateEnabledUseCase)
    factoryOf(::SetUpdateEnabledUseCase)
    viewModelOf(::UpdateViewModel)
}