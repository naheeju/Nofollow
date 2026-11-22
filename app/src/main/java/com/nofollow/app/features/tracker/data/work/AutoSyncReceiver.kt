package com.nofollow.app.features.tracker.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pintu masuk alarm sinkron otomatis ([ACTION_TICK]) dan pemulih jadwal setelah perangkat restart atau app diperbarui
 * (alarm hilang di kedua kejadian itu). Pekerjaan beratnya tidak di sini: receiver cuma menanyakan ke
 * [AutoSyncScheduler] apakah sudah waktunya, lalu melempar sinkron ke worker foreground.
 */
class AutoSyncReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: AutoSyncScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (action == ACTION_TICK) scheduler.onAlarm() else scheduler.reschedule()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "receiver gagal ($action)", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.nofollow.app.autosync.TICK"
        private const val TAG = "NofollowAutoSync"
    }
}