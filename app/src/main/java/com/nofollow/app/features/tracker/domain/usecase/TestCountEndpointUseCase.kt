package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.probe.CountEndpointProbe

/**
 * Pembungkus tipis di domain. Depend ke interface [CountEndpointProbe], bukan ke
 * implementasi data-nya langsung, supaya UI/domain tidak perlu tahu detail infrastruktur (hit network).
 */
class TestCountEndpointUseCase(
    private val probe: CountEndpointProbe,
) {
    suspend operator fun invoke(): List<String> = probe.run()
}