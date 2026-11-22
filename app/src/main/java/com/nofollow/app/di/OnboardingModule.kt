package com.nofollow.app.di

import com.nofollow.app.features.onboarding.data.repository.OnboardingRepositoryImpl
import com.nofollow.app.features.onboarding.domain.repository.OnboardingRepository
import com.nofollow.app.features.onboarding.domain.usecase.MarkOnboardingSeenUseCase
import com.nofollow.app.features.onboarding.domain.usecase.ObserveOnboardingSeenUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val onboardingModule = module {
    singleOf(::OnboardingRepositoryImpl) bind OnboardingRepository::class

    factoryOf(::ObserveOnboardingSeenUseCase)
    factoryOf(::MarkOnboardingSeenUseCase)
}