package com.nofollow.app.di

import com.nofollow.app.features.auth.data.repository.SessionRepositoryImpl
import com.nofollow.app.features.auth.domain.repository.SessionRepository
import com.nofollow.app.features.auth.domain.usecase.LogoutUseCase
import com.nofollow.app.features.auth.domain.usecase.ObserveLoginStateUseCase
import com.nofollow.app.features.auth.domain.usecase.SaveSessionUseCase
import com.nofollow.app.features.auth.ui.LoginViewModel
import com.nofollow.app.navigation.AppViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val authModule = module {
    singleOf(::SessionRepositoryImpl) bind SessionRepository::class

    factoryOf(::SaveSessionUseCase)
    factoryOf(::ObserveLoginStateUseCase)
    factoryOf(::LogoutUseCase)

    viewModelOf(::LoginViewModel)
    viewModelOf(::AppViewModel)
}