package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.repository.FollowerHistoryRepository

class ObserveFollowerHistoryUseCase(private val repository: FollowerHistoryRepository) {
    operator fun invoke(days: Int) = repository.observe(days)
}