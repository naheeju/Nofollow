package com.nofollow.app

import android.app.Application
import androidx.work.WorkManager
import com.nofollow.app.core.common.AppForeground
import com.nofollow.app.di.authModule
import com.nofollow.app.di.coreModule
import com.nofollow.app.di.homeModule
import com.nofollow.app.di.kepoModule
import com.nofollow.app.di.onboardingModule
import com.nofollow.app.di.trackerModule
import com.nofollow.app.di.updateModule
import com.nofollow.app.features.tracker.data.notify.UnfollowNotifier
import com.nofollow.app.features.tracker.data.work.AutoSyncScheduler
import com.nofollow.app.features.update.data.notify.UpdateNotifier
import com.nofollow.app.features.update.data.work.UpdateCheckScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppForeground.register(this)
        val koin = startKoin {
            androidLogger()
            androidContext(this@App)
            modules(coreModule, authModule, onboardingModule, trackerModule, homeModule, kepoModule, updateModule)
        }.koin

        koin.get<UnfollowNotifier>().ensureChannel()
        // Pekerjaan periodik lama (SyncWorker tiap 15 menit) sudah dihapus: buang sisanya dari database WorkManager.
        WorkManager.getInstance(this).cancelUniqueWork(LEGACY_TICK_WORK)
        // Sinkron otomatis: satu alarm terjadwal; saat bangun menjalankan sinkron yang sama dengan tombol Sinkron.
        koin.get<AutoSyncScheduler>().requestReschedule()
        // Cek pembaruan harian di latar belakang; hanya terjadwal selama toggle "Cek pembaruan" aktif.
        koin.get<UpdateNotifier>().ensureChannel()
        koin.get<UpdateCheckScheduler>().start()
    }

    private companion object {
        const val LEGACY_TICK_WORK = "nofollow_sync_tick"
    }
}