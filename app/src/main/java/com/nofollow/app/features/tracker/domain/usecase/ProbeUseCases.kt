package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.repository.FollowerSyncRepository
import com.nofollow.app.features.tracker.domain.repository.ProbeControl

class ProbeFollowersUseCase(private val repository: FollowerSyncRepository) {
    suspend operator fun invoke() = repository.probe()
    suspend fun nextDelayMs() = repository.nextPulseDelayMs()
}

class ObserveProbeEnabledUseCase(private val control: ProbeControl) {
    operator fun invoke() = control.enabled
}

class SetProbeEnabledUseCase(private val control: ProbeControl) {
    suspend operator fun invoke(enabled: Boolean) = control.setEnabled(enabled)
}

class EnsureProbeRunningUseCase(private val control: ProbeControl) {
    suspend operator fun invoke() = control.ensureRunning()
}