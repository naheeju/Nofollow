package com.nofollow.app.features.home.domain.usecase

import com.nofollow.app.features.tracker.domain.usecase.EnsureProbeRunningUseCase
import com.nofollow.app.features.tracker.domain.usecase.MarkEventsReadUseCase
import com.nofollow.app.features.tracker.domain.usecase.RefreshAccountProfileUseCase
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Tugas ringan tiap Beranda tampil: tandai unfollow sebagai sudah dibaca, pastikan pantau cepat hidup,
 * dan perbarui profil kalau sudah kedaluwarsa. Berjalan paralel; sinkron dipisah di [SyncAllUseCase].
 */
class OnHomeOpenedUseCase(
    private val markEventsRead: MarkEventsReadUseCase,
    private val ensureProbeRunning: EnsureProbeRunningUseCase,
    private val refreshProfile: RefreshAccountProfileUseCase,
) {
    suspend operator fun invoke() = coroutineScope {
        launch { markEventsRead() }
        launch { ensureProbeRunning() }
        launch { refreshProfile() }
    }
}