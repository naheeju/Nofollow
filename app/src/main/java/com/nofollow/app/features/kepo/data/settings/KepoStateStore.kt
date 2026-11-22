package com.nofollow.app.features.kepo.data.settings

import android.content.Context
import androidx.core.content.edit
import com.nofollow.app.features.kepo.data.remote.KepoParser
import org.json.JSONArray
import org.json.JSONObject

/**
 * Status polling Kepo (bukan data pribadi): kapan terakhir cek tray dan penonton, dan story aktif yang dipantau.
 * Disimpan sebagai JSON kecil di SharedPreferences, satu akun saja (akun lain dianggap kosong).
 */
class KepoStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    data class Snapshot(
        val ownerId: String,
        val trayAt: Long,
        val pollAt: Long,
        val stories: List<KepoParser.Story>,
    )

    fun load(owner: String): Snapshot? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(raw)
            if (o.getString("owner") != owner) return null
            val arr = o.getJSONArray("stories")
            Snapshot(
                ownerId = owner,
                trayAt = o.optLong("trayAt", 0L),
                pollAt = o.optLong("pollAt", 0L),
                stories = (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    KepoParser.Story(s.getString("pk"), s.optLong("takenAt"), s.optLong("expiringAt"))
                },
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(s: Snapshot) {
        val arr = JSONArray()
        s.stories.forEach {
            arr.put(JSONObject().put("pk", it.pk).put("takenAt", it.takenAt).put("expiringAt", it.expiringAt))
        }
        val json = JSONObject()
            .put("owner", s.ownerId)
            .put("trayAt", s.trayAt)
            .put("pollAt", s.pollAt)
            .put("stories", arr)
            .toString()
        prefs.edit { putString(KEY, json) }
    }

    private companion object {
        const val FILE_NAME = "nofollow_kepo"
        const val KEY = "state"
    }
}