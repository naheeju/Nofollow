package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.repository.TrackerLogRepository

class ObserveTrackerLogUseCase(private val repository: TrackerLogRepository) {
    operator fun invoke() = repository.entries
}

class ObserveNotFollowingBackUseCase(private val repository: TrackerLogRepository) {
    operator fun invoke() = repository.notFollowingBack
}

class ObserveAudienceListsUseCase(private val repository: TrackerLogRepository) {
    operator fun invoke() = repository.audienceLists
}

class ObserveUnseenTrackerCountUseCase(private val repository: TrackerLogRepository) {
    operator fun invoke() = repository.unseenCount
}

class MarkTrackerSeenUseCase(private val repository: TrackerLogRepository) {
    suspend operator fun invoke() = repository.markSeen()
}

class ObserveCheckedEntriesUseCase(private val repository: TrackerLogRepository) {
    operator fun invoke() = repository.checkedKeys
}

class SetEntryCheckedUseCase(private val repository: TrackerLogRepository) {
    suspend operator fun invoke(key: String, checked: Boolean) = repository.setChecked(key, checked)
}