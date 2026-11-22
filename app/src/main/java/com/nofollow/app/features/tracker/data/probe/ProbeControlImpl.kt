package com.nofollow.app.features.tracker.data.probe

import com.nofollow.app.features.tracker.data.settings.SyncSettings
import com.nofollow.app.features.tracker.data.work.AutoSyncScheduler
import com.nofollow.app.features.tracker.domain.repository.ProbeControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Saklar "sinkron otomatis di latar belakang". Nama ProbeControl dipertahankan supaya UI dan use case tidak perlu
 * diubah; ProbeService sudah diganti [AutoSyncScheduler], yang menjalankan sinkron yang sama dengan tombol Sinkron.
 * Aktif berarti ada alarm terjadwal; mati berarti alarm dibatalkan.
 */
class ProbeControlImpl(
    private val settings: SyncSettings,
    private val scheduler: AutoSyncScheduler,
) : ProbeControl {

    override val enabled: Flow<Boolean> = settings.probeEnabled

    override suspend fun setEnabled(enabled: Boolean) {
        settings.setProbeEnabled(enabled)
        if (enabled) scheduler.reschedule() else scheduler.cancel()
    }

    /** Dipanggil tiap Beranda tampil: pulihkan alarm yang mungkin hilang (force-stop, restart). */
    override suspend fun ensureRunning() {
        if (settings.probeEnabled.first()) scheduler.reschedule()
    }
}