package com.nofollow.app.features.home.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nofollow.app.features.home.domain.model.HomeSyncResult
import com.nofollow.app.features.home.domain.repository.HomeSyncJobs
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class HomeSyncJobsImpl(
    context: Context,
    private val store: HomeSyncResultStore,
) : HomeSyncJobs {
    private val work = WorkManager.getInstance(context.applicationContext)

    override val running: Flow<Boolean> = work.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)
        .map { infos -> infos.any { !it.state.isFinished } }
        .distinctUntilChanged()

    override val automaticRunning: Flow<Boolean> = work.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)
        .map { infos -> infos.any { !it.state.isFinished && TAG_AUTO in it.tags } }
        .distinctUntilChanged()

    override val pendingResult: Flow<HomeSyncResult?> = store.pending

    override suspend fun start(trigger: SyncTrigger) {
        val request = OneTimeWorkRequestBuilder<HomeSyncWorker>()
            .setInputData(workDataOf(HomeSyncWorker.KEY_TRIGGER to trigger.name))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .apply {
                // Otomatis: tunggu jaringan dulu, supaya tidak bangun di sinyal mati lalu gagal. Tombol manual dan
                // sinkron pertama tanpa syarat jaringan: kalau offline, langsung gagal dan hasilnya terlapor.
                if (trigger == SyncTrigger.SCHEDULED) {
                    addTag(TAG_AUTO)
                    setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                }
            }
            .build()

        // KEEP: tombol ditekan berulang atau app dibuka lagi saat sinkron berjalan tidak membuat sinkron kedua.
        // Pengecualian: tombol manual mendahului sinkron otomatis (yang dibatalkan; manual lanjut dari cursor).
        val policy = if (trigger == SyncTrigger.MANUAL && automaticActive()) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }
        work.enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    override suspend fun consumeResult() = store.clear()

    private suspend fun automaticActive(): Boolean = automaticRunning.first()

    private companion object {
        const val UNIQUE_NAME = "nofollow_home_sync"
        const val TAG_AUTO = "nofollow_auto"
    }
}