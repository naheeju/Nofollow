package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.ProbeResult
import com.nofollow.app.features.tracker.domain.model.SyncOutcome
import com.nofollow.app.features.tracker.domain.model.SyncTrigger

interface FollowerSyncRepository {
    /**
     * Satu kali jalan sinkronisasi yang aman dipanggil kapan saja: repository sendiri memutuskan
     * lanjut, mulai sweep baru, atau tidak melakukan apa-apa (jam tenang, cooldown, belum waktunya,
     * plafon harian). Halt dan cooldown selalu dihormati, apa pun pemicunya.
     */
    suspend fun sync(trigger: SyncTrigger): SyncOutcome

    /**
     * Pantau cepat: apakah sweep cepat boleh jalan sekarang (baseline selesai, tidak halt / cooldown /
     * jam istirahat). Tidak mengirim request; pemanggil yang menjalankan `sync(SyncTrigger.PROBE)`.
     */
    suspend fun probe(): ProbeResult

    /** Jeda sampai sweep cepat berikutnya: makin banyak follower makin jarang, supaya request harian terkendali. */
    suspend fun nextPulseDelayMs(): Long

    /** Dipanggil setelah user menyelesaikan checkpoint / login ulang. */
    suspend fun clearHalt()
}