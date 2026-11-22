package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.repository.AudienceRepository
import com.nofollow.app.features.tracker.domain.repository.FollowingSyncRepository

class ObserveAudienceSummaryUseCase(private val repository: AudienceRepository) {
    operator fun invoke() = repository.summary
}

class SyncFollowingUseCase(private val repository: FollowingSyncRepository) {
    suspend operator fun invoke(manual: Boolean, full: Boolean = manual) = repository.sync(manual, full)
}