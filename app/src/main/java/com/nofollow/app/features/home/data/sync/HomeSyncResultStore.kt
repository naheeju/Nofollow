package com.nofollow.app.features.home.data.sync

import android.content.Context
import com.nofollow.app.features.home.domain.model.HomeSyncResult
import com.nofollow.app.features.home.domain.model.HomeSyncSkip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Satu hasil sinkron latar belakang yang belum dilihat. Disimpan di SharedPreferences supaya selamat dari
 * proses yang mati: worker menulis, Beranda membaca begitu app dibuka lagi.
 */
class HomeSyncResultStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _pending = MutableStateFlow(decode(prefs.getString(KEY, null)))
    val pending: StateFlow<HomeSyncResult?> = _pending.asStateFlow()

    fun put(result: HomeSyncResult) {
        prefs.edit().putString(KEY, encode(result)).apply()
        _pending.value = result
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _pending.value = null
    }

    private fun encode(r: HomeSyncResult): String = when (r) {
        is HomeSyncResult.Synced -> "S|${r.kepoInteractions ?: ""}"
        HomeSyncResult.InProgress -> "P"
        HomeSyncResult.Aborted -> "A"
        HomeSyncResult.Failed -> "F"
        is HomeSyncResult.Halted -> "H|${r.reason}"
        is HomeSyncResult.Skipped -> "K|${r.reason.name}|${r.until ?: ""}"
    }

    private fun decode(raw: String?): HomeSyncResult? {
        if (raw.isNullOrEmpty()) return null
        return when (raw.substringBefore('|')) {
            "S" -> HomeSyncResult.Synced(raw.substringAfter('|', "").toIntOrNull())
            "P" -> HomeSyncResult.InProgress
            "A" -> HomeSyncResult.Aborted
            "F" -> HomeSyncResult.Failed
            "H" -> HomeSyncResult.Halted(raw.substringAfter('|', ""))
            "K" -> {
                val parts = raw.split('|', limit = 3)
                val reason = runCatching { HomeSyncSkip.valueOf(parts.getOrElse(1) { "" }) }.getOrNull() ?: return null
                HomeSyncResult.Skipped(reason, parts.getOrNull(2)?.toLongOrNull())
            }
            else -> null
        }
    }

    private companion object {
        const val FILE = "home_sync_result"
        const val KEY = "pending"
    }
}