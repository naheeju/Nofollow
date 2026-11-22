package com.nofollow.app.features.update.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nofollow.app.features.update.domain.repository.UpdateSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Menjaga pekerjaan harian [UpdateCheckWorker] mengikuti toggle "Cek pembaruan": aktif = terjadwal (sekali sehari,
 * hanya saat ada internet), mati = dibatalkan. Cukup dipanggil [start] sekali dari App; perubahan toggle ikut otomatis.
 * Jeda awal 12 jam karena cek pertama sudah dilakukan saat app dibuka.
 */
class UpdateCheckScheduler(
    private val context: Context,
    private val settings: UpdateSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            settings.enabled.collect { enabled ->
                val work = WorkManager.getInstance(context)
                if (enabled) {
                    // KEEP: jadwal yang sudah ada tidak di-reset tiap app dibuka.
                    work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request())
                } else {
                    work.cancelUniqueWork(WORK_NAME)
                }
            }
        }
    }

    private fun request() = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(12, TimeUnit.HOURS)
        .build()

    private companion object {
        const val WORK_NAME = "nofollow_update_check"
    }
}