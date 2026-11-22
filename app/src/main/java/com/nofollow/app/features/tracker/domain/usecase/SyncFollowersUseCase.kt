package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import com.nofollow.app.features.tracker.domain.repository.FollowerSyncRepository

class SyncFollowersUseCase(private val repository: FollowerSyncRepository) {
    suspend operator fun invoke(trigger: SyncTrigger) = repository.sync(trigger)
    suspend fun clearHalt() = repository.clearHalt()
}