package com.nofollow.app.features.tracker.domain.probe

/**
 * Port domain untuk tes sekali-jalan "seberapa bisa jumlah follower dibaca dari luar?".
 * Implementasinya ([com.nofollow.app.features.tracker.data.probe.CountEndpointTester]) ada di data
 * karena dia hit network; domain/UI cukup kenal lewat interface ini.
 */
interface CountEndpointProbe {
    suspend fun run(): List<String>
}