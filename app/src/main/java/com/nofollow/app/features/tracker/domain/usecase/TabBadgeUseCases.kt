package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.model.TrackerTab
import com.nofollow.app.features.tracker.domain.repository.TabBadgeRepository

class ObserveTabBadgesUseCase(private val repository: TabBadgeRepository) {
    operator fun invoke() = repository.badges
}

class MarkTabSeenUseCase(private val repository: TabBadgeRepository) {
    suspend operator fun invoke(tab: TrackerTab) = repository.markSeen(tab)
}