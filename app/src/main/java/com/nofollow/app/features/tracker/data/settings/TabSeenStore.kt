package com.nofollow.app.features.tracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nofollow.app.features.tracker.domain.model.TrackerTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.tabSeenDataStore by preferencesDataStore(name = "tab_seen")

/**
 * Penanda "sudah dilihat" per kategori Tracker, per akun, untuk lencana kecil di menu Beranda.
 * - Unfollowers: id kejadian terakhir yang sudah dilihat (kejadian di atasnya = baru).
 * - Tidak follow balik, Fans, Mutual: daftar PK saat terakhir dilihat (PK yang tidak ada di sana = baru).
 * Terpisah dari [TrackerSeenStore] (lencana bilah navigasi, hilang begitu Tracker terbuka di kategori mana pun).
 * Sengaja DataStore, bukan tabel Room: database tidak perlu migrasi.
 */
class TabSeenStore(private val context: Context) {

    data class Seen(
        /** Null selama belum pernah diinisialisasi. */
        val unfollowId: Long?,
        /** Kategori yang belum pernah diinisialisasi tidak ada di map. */
        val pks: Map<TrackerTab, Set<String>>,
    )

    fun seen(owner: String): Flow<Seen> = context.tabSeenDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            Seen(
                unfollowId = prefs[unfollowKey(owner)],
                pks = ListTabs.mapNotNull { tab -> prefs[pksKey(owner, tab)]?.let { tab to decode(it) } }.toMap(),
            )
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()

    /** Pemakaian pertama: semua kejadian yang sudah ada dianggap dilihat, supaya lencana tidak langsung berisi ratusan. */
    suspend fun initUnfollowIfAbsent(owner: String, id: Long) {
        context.tabSeenDataStore.edit { prefs ->
            if (prefs[unfollowKey(owner)] == null) prefs[unfollowKey(owner)] = id
        }
    }

    /** Id hanya boleh maju: dua pemanggil bersamaan tidak bisa memundurkan penanda. */
    suspend fun markUnfollowSeen(owner: String, id: Long) {
        context.tabSeenDataStore.edit { prefs ->
            val current = prefs[unfollowKey(owner)]
            if (current == null || id > current) prefs[unfollowKey(owner)] = id
        }
    }

    /** Hanya kategori yang belum punya penanda yang diisi; yang sudah ada dibiarkan. */
    suspend fun initPksIfAbsent(owner: String, lists: Map<TrackerTab, Set<String>>) {
        context.tabSeenDataStore.edit { prefs ->
            lists.forEach { (tab, pks) ->
                if (prefs[pksKey(owner, tab)] == null) prefs[pksKey(owner, tab)] = encode(pks)
            }
        }
    }

    suspend fun setPks(owner: String, tab: TrackerTab, pks: Set<String>) {
        context.tabSeenDataStore.edit { it[pksKey(owner, tab)] = encode(pks) }
    }

    private fun encode(pks: Set<String>): String = JSONArray(pks).toString()

    private fun decode(raw: String): Set<String> = runCatching {
        val arr = JSONArray(raw)
        HashSet<String>(arr.length()).also { set -> for (i in 0 until arr.length()) set += arr.getString(i) }
    }.getOrDefault(emptySet())

    private fun unfollowKey(owner: String): Preferences.Key<Long> = longPreferencesKey("unf_$owner")

    private fun pksKey(owner: String, tab: TrackerTab): Preferences.Key<String> =
        stringPreferencesKey("pks_${owner}_${tab.name}")

    private companion object {
        val ListTabs = listOf(TrackerTab.NOT_FOLLOWING_BACK, TrackerTab.FANS, TrackerTab.MUTUAL)
    }
}