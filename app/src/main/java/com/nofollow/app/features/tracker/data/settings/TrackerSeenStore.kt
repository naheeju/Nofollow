package com.nofollow.app.features.tracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.trackerSeenDataStore by preferencesDataStore(name = "tracker_seen")

/**
 * Id kejadian terakhir yang sudah dilihat user di layar Tracker, per akun. Kejadian dengan id di atasnya dihitung
 * "baru" untuk lencana di bilah navigasi. Sengaja DataStore, bukan tabel Room: database tidak perlu migrasi.
 * Memakai id (bukan waktu) supaya lencana tidak meleset karena beda jam atau kejadian yang tersimpan belakangan.
 */
class TrackerSeenStore(private val context: Context) {

    /** Null selama belum pernah diinisialisasi untuk [owner]. */
    fun seenId(owner: String): Flow<Long?> = context.trackerSeenDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[key(owner)] }
        .distinctUntilChanged()

    /** Id hanya boleh maju: dua pemanggil bersamaan tidak bisa memundurkan penanda. */
    suspend fun markSeen(owner: String, id: Long) {
        context.trackerSeenDataStore.edit { prefs ->
            val current = prefs[key(owner)]
            if (current == null || id > current) prefs[key(owner)] = id
        }
    }

    /** Pemakaian pertama: anggap semua yang sudah ada sebagai sudah dilihat, supaya lencana tidak langsung berisi ratusan. */
    suspend fun initIfAbsent(owner: String, id: Long) {
        context.trackerSeenDataStore.edit { prefs ->
            if (prefs[key(owner)] == null) prefs[key(owner)] = id
        }
    }

    // Nama kunci baru ("seenid_"), terpisah dari penanda waktu versi lama yang tidak dipakai lagi.
    private fun key(owner: String): Preferences.Key<Long> = longPreferencesKey("seenid_$owner")
}