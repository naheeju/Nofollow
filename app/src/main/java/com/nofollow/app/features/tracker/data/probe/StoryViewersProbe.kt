package com.nofollow.app.features.tracker.domain.probe

/**
 * Port domain untuk tes sekali-jalan "bisakah daftar penonton dan like story dibaca?".
 * Implementasinya ([com.nofollow.app.features.tracker.data.probe.StoryViewersTester]) ada di data karena hit network.
 */
interface StoryViewersProbe {
    suspend fun run(): List<String>
}