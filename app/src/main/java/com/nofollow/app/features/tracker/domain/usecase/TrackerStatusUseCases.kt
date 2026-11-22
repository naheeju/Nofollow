package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.model.SyncMode
import com.nofollow.app.features.tracker.domain.repository.TrackerStatusRepository

class ObserveTrackerStatusUseCase(private val repository: TrackerStatusRepository) {
    operator fun invoke() = repository.observeStatus()
}

class SetSyncModeUseCase(private val repository: TrackerStatusRepository) {
    suspend operator fun invoke(mode: SyncMode) = repository.setMode(mode)
}

class MarkEventsReadUseCase(private val repository: TrackerStatusRepository) {
    suspend operator fun invoke() = repository.markEventsRead()
}