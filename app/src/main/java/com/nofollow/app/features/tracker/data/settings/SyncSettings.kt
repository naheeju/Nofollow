package com.nofollow.app.features.tracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nofollow.app.features.tracker.domain.model.SyncMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.syncDataStore by preferencesDataStore(name = "sync_settings")

/** Pengaturan sinkronisasi yang dipilih user. */
class SyncSettings(private val context: Context) {

    private val data: Flow<androidx.datastore.preferences.core.Preferences> = context.syncDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val mode: Flow<SyncMode> = data
        .map { SyncMode.fromKey(it[KEY_MODE]) }
        .distinctUntilChanged()

    suspend fun setMode(mode: SyncMode) {
        context.syncDataStore.edit { it[KEY_MODE] = mode.key }
    }

    /** Pantau cepat (foreground service + probe jumlah follower) aktif atau tidak. */
    val probeEnabled: Flow<Boolean> = data
        .map { it[KEY_PROBE_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setProbeEnabled(enabled: Boolean) {
        context.syncDataStore.edit { it[KEY_PROBE_ENABLED] = enabled }
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("sync_mode")
        val KEY_PROBE_ENABLED = booleanPreferencesKey("probe_enabled")
    }
}