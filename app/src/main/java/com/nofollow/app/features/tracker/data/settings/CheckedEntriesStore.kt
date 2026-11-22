package com.nofollow.app.features.tracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.checkedDataStore by preferencesDataStore(name = "checked_entries")

/**
 * Kejadian yang sudah ditandai "dicek" user di layar Tracker (supaya tahu sampai mana unfollow-nya diurus).
 * Murni lokal. Kunci berbentuk "pk@waktuTerdeteksi"; saat terlalu banyak, yang paling lama dibuang.
 * Sengaja DataStore, bukan tabel Room: database memakai fallbackToDestructiveMigration.
 */
class CheckedEntriesStore(private val context: Context) {

    val keys: Flow<Set<String>> = context.checkedDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY].orEmpty() }
        .distinctUntilChanged()

    suspend fun set(key: String, checked: Boolean) {
        context.checkedDataStore.edit { prefs ->
            val current = prefs[KEY].orEmpty()
            val next = if (checked) current + key else current - key
            prefs[KEY] = if (next.size > MAX_KEYS) trim(next) else next
        }
    }

    /** Pertahankan [KEEP_KEYS] yang paling baru menurut waktu di belakang '@'. */
    private fun trim(keys: Set<String>): Set<String> =
        keys.sortedByDescending { it.substringAfter('@', "0").toLongOrNull() ?: 0L }.take(KEEP_KEYS).toSet()

    private companion object {
        val KEY = stringSetPreferencesKey("checked_keys")
        const val MAX_KEYS = 2_000
        const val KEEP_KEYS = 1_000
    }
}