package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.repository.AccountProfileRepository

class ObserveAccountProfileUseCase(private val repository: AccountProfileRepository) {
    operator fun invoke() = repository.profile
}

class RefreshAccountProfileUseCase(private val repository: AccountProfileRepository) {
    suspend operator fun invoke() = repository.refresh()
}