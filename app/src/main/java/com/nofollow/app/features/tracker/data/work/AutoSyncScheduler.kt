package com.nofollow.app.features.tracker.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.local.SyncStateDao
import com.nofollow.app.features.tracker.data.settings.SyncSettings
import com.nofollow.app.features.tracker.data.sync.SyncDecision
import com.nofollow.app.features.tracker.data.sync.SyncPlanner
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * Penjadwal sinkron otomatis. Tidak melakukan sweep sendiri: tugasnya hanya membangunkan app di waktu yang tepat,
 * lalu menjalankan sinkron yang SAMA dengan tombol Sinkron (worker foreground + notifikasi "Menyinkron data"),
 * dengan trigger SCHEDULED supaya pacing pelan, kantong jatah latar belakang, dan jam tenang tetap berlaku.
 *
 * Jadwalnya satu alarm: waktunya diambil dari [SyncPlanner] (nextSweepAt hasil mode + jitter, cooldown, jam tenang).
 * Alarm yang bangun lebih dulu memeriksa apakah sinkron memang sudah waktunya; kalau belum, cuma menjadwal ulang
 * tanpa menyalakan notifikasi apa pun. Jadi tidak ada notifikasi yang berkedip untuk "tidak ada yang dikerjakan".
 *
 * Alarm hilang kalau perangkat restart atau app di-force-stop. Restart dipulihkan [AutoSyncReceiver]; selebihnya
 * dipulihkan tiap app dibuka ([requestReschedule] dari App dan Beranda).
 *
 * [startSync] dipasang di DI (menunjuk ke HomeSyncJobs) supaya data layer tracker tidak mengenal fitur home.
 */
class AutoSyncScheduler(
    private val context: Context,
    private val syncDao: SyncStateDao,
    private val sessionStore: SessionStore,
    private val config: SyncConfig,
    private val settings: SyncSettings,
    private val startSync: suspend () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private sealed interface Plan {
        data object RunNow : Plan
        data class At(val ms: Long) : Plan
        data object None : Plan
    }

    init {
        // Mode diganti: TrackerStatusRepository menghitung ulang nextSweepAt sesaat setelah menyimpan mode,
        // jadi beri jeda singkat sebelum jadwal dibaca ulang.
        scope.launch {
            settings.mode.drop(1).collect {
                delay(MODE_SETTLE_MS)
                reschedule()
            }
        }
    }

    /** Hitung ulang jadwal tanpa menunggu. [minDelayMs] = jeda minimal dari sekarang (mis. setelah sweep terpotong). */
    fun requestReschedule(minDelayMs: Long = 0L) {
        scope.launch {
            try {
                reschedule(minDelayMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "jadwal ulang gagal", e)
            }
        }
    }

    fun cancel() {
        alarms.cancel(tickIntent())
        Log.d(TAG, "alarm sinkron otomatis dibatalkan")
    }

    /** Alarm bangun: kalau sinkron sudah waktunya, jalankan; kalau belum, jadwalkan ulang saja. */
    suspend fun onAlarm() {
        if (!settings.probeEnabled.first()) return
        val now = System.currentTimeMillis()
        if (plan(now) is Plan.RunNow) {
            // Jaring pengaman: kalau proses mati sebelum selesai, alarm ini yang membangunkan lagi.
            // Worker menimpanya dengan jadwal sebenarnya begitu selesai.
            lock.withLock { setAlarm(now + SAFETY_MS) }
            Log.d(TAG, "sinkron otomatis dimulai")
            startSync()
        } else {
            reschedule()
        }
    }

    suspend fun reschedule(minDelayMs: Long = 0L) = lock.withLock {
        if (!settings.probeEnabled.first()) {
            cancel()
            return@withLock
        }
        val now = System.currentTimeMillis()
        val target = when (val p = plan(now)) {
            Plan.None -> {
                cancel()
                return@withLock
            }
            Plan.RunNow -> now
            is Plan.At -> p.ms
        }
        val earliest = now + maxOf(minDelayMs, config.autoMinTickMs)
        setAlarm(avoidQuietHours(maxOf(target, earliest)))
    }

    // ---------------------------------------------------------------- rencana

    private suspend fun plan(now: Long): Plan {
        val owner = sessionStore.session.value?.userId ?: return Plan.None
        val state = syncDao.get(owner)
        return when (val d = SyncPlanner.decide(state, now, hourOf(now), SyncTrigger.SCHEDULED, config)) {
            // Berhenti sampai user bertindak: tidak ada alarm. Dijadwalkan lagi saat app dibuka setelah masalahnya beres.
            is SyncDecision.Halt -> Plan.None
            is SyncDecision.CoolingDown -> Plan.At(d.until)
            is SyncDecision.TooSoon -> Plan.At(d.until)
            // Jam tenang: avoidQuietHours() yang menggesernya ke ujung jam tenang.
            SyncDecision.QuietHours -> Plan.At(now)
            SyncDecision.Idle -> Plan.At(state?.nextSweepAt ?: (now + IDLE_RECHECK_MS))
            SyncDecision.Resume, is SyncDecision.Start -> Plan.RunNow
        }
    }

    /** Waktu di jam tenang digeser ke akhir jam tenang (plus jitter), supaya tidak membangunkan HP tengah malam. */
    private fun avoidQuietHours(at: Long): Long {
        var t = at
        var steps = 0
        while (SyncPlanner.inQuietHours(hourOf(t), config) && steps++ < MAX_QUIET_STEPS) t += QUIET_STEP_MS
        return if (t == at) at else t + Random.nextLong(0, QUIET_STEP_MS)
    }

    private fun hourOf(ms: Long): Int =
        Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

    // ----------------------------------------------------------------- alarm

    private fun setAlarm(at: Long) {
        // Alarm tepat waktu (USE_EXACT_ALARM): app yang dibangunkan alarm jenis ini boleh memulai foreground service
        // dari latar belakang, jadi notifikasi "Menyinkron data" muncul. Dengan alarm tidak tepat, Android 12+ menolak
        // setForeground dari latar belakang dan sinkron jalan tanpa notifikasi. Jitter jadwal tetap dari SyncPlanner.
        // Cadangan: izin tidak diberikan, pakai alarm biasa.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, tickIntent())
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, tickIntent())
        }
        Log.d(TAG, "alarm sinkron otomatis ${(at - System.currentTimeMillis()) / 1000} dtk lagi (exact=$exact)")
    }

    private fun tickIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AutoSyncReceiver::class.java).setAction(AutoSyncReceiver.ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "NofollowAutoSync"
        const val REQUEST_CODE = 41
        const val SAFETY_MS = 30 * 60_000L
        const val IDLE_RECHECK_MS = 30 * 60_000L
        const val MODE_SETTLE_MS = 1_500L
        const val QUIET_STEP_MS = 15 * 60_000L
        const val MAX_QUIET_STEPS = 96
    }
}