package com.nofollow.app.features.onboarding.domain.usecase

import com.nofollow.app.features.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow

class ObserveOnboardingSeenUseCase(private val repository: OnboardingRepository) {
    operator fun invoke(): Flow<Boolean> = repository.observeSeen()
}

class MarkOnboardingSeenUseCase(private val repository: OnboardingRepository) {
    suspend operator fun invoke() = repository.markSeen()
}