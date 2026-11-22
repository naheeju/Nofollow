package com.nofollow.app.features.update.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nofollow.app.features.update.domain.model.AppVersion
import com.nofollow.app.features.update.domain.repository.UpdateSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.updateDataStore by preferencesDataStore(name = "update_settings")

/** Pilihan dan status cek pembaruan (bukan data pribadi), disimpan di DataStore. */
class UpdateSettingsStore(private val context: Context) : UpdateSettings {

    private val data: Flow<Preferences> = context.updateDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val enabled: Flow<Boolean> = data
        .map { it[KEY_ENABLED] ?: true }
        .distinctUntilChanged()

    override val availableVersion: Flow<AppVersion?> = data
        .map { AppVersion.parseOrNull(it[KEY_AVAILABLE]) }
        .distinctUntilChanged()

    override val dismissedVersion: Flow<AppVersion?> = data
        .map { AppVersion.parseOrNull(it[KEY_DISMISSED]) }
        .distinctUntilChanged()

    override suspend fun lastCheckedAt(): Long = data.first()[KEY_LAST_CHECKED] ?: 0L

    override suspend fun notifiedVersion(): AppVersion? = AppVersion.parseOrNull(data.first()[KEY_NOTIFIED])

    override suspend fun setEnabled(on: Boolean) {
        context.updateDataStore.edit { it[KEY_ENABLED] = on }
    }

    override suspend fun recordCheck(at: Long, latest: AppVersion?) {
        context.updateDataStore.edit {
            it[KEY_LAST_CHECKED] = at
            if (latest != null) it[KEY_AVAILABLE] = latest.toString()
        }
    }

    override suspend fun setDismissed(version: AppVersion) {
        context.updateDataStore.edit { it[KEY_DISMISSED] = version.toString() }
    }

    override suspend fun setNotified(version: AppVersion) {
        context.updateDataStore.edit { it[KEY_NOTIFIED] = version.toString() }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("update_enabled")
        val KEY_LAST_CHECKED = longPreferencesKey("update_last_checked_at")
        val KEY_AVAILABLE = stringPreferencesKey("update_available_version")
        val KEY_DISMISSED = stringPreferencesKey("update_dismissed_version")
        val KEY_NOTIFIED = stringPreferencesKey("update_notified_version")
    }
}