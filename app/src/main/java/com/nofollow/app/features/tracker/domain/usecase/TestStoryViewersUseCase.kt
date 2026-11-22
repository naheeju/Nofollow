package com.nofollow.app.features.tracker.domain.usecase

import com.nofollow.app.features.tracker.domain.probe.StoryViewersProbe

/** Pembungkus tipis di domain, sama polanya dengan [TestCountEndpointUseCase]. */
class TestStoryViewersUseCase(
    private val probe: StoryViewersProbe,
) {
    suspend operator fun invoke(): List<String> = probe.run()
}