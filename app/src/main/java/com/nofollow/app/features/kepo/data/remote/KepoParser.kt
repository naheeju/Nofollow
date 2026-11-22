package com.nofollow.app.features.kepo.data.remote

import org.json.JSONObject

/**
 * Parser murni (tanpa Android/network) untuk dua respons yang sudah terbukti lewat StoryViewersTester:
 * - feed/reels_tray/ : tray[] -> item milik kita -> items[] (story aktif)
 * - media/{pk}/list_reel_media_viewer/ : viewers[] -> { user{...friendship_status}, has_liked, reply_text }, next_max_id
 *
 * `reactions` (emoji) belum pernah terisi di log, jadi bentuknya belum diketahui dan sengaja tidak dibaca.
 */
object KepoParser {
    data class Story(val pk: String, val takenAt: Long, val expiringAt: Long)

    data class Viewer(
        val pk: String,
        val username: String,
        val fullName: String,
        val picUrl: String?,
        val followedBy: Boolean,
        val hasLiked: Boolean,
        val replied: Boolean,
        /** Close friend kita (friendship_status.is_bestie): sinyal kedekatan, dipakai untuk mengurutkan penonton biasa. */
        val isBestie: Boolean,
        /** Penanda spam dari Instagram (is_spam_viewer): tidak dicatat dan tidak ikut mengisi slot. */
        val isSpam: Boolean,
    )

    data class ViewerPage(val viewers: List<Viewer>, val nextMaxId: String?, val total: Int?)

    /** null = format tidak dikenali. Daftar kosong = tidak ada story aktif. */
    fun parseStories(body: String, owner: String): List<Story>? {
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val tray = root.optJSONArray("tray") ?: return null
        for (i in 0 until tray.length()) {
            val reel = tray.optJSONObject(i) ?: continue
            val mine = reel.str("id") == owner || reel.optJSONObject("user")?.str("pk") == owner
            if (!mine) continue
            val items = reel.optJSONArray("items") ?: return emptyList()
            val out = mutableListOf<Story>()
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                val pk = item.str("pk").ifBlank { item.str("id").substringBefore('_') }
                if (pk.isBlank()) continue
                val takenAt = item.optLong("taken_at", 0L)
                val expiringAt = item.optLong("expiring_at", 0L).takeIf { it > 0 } ?: (takenAt + 24 * 3600)
                out += Story(pk, takenAt, expiringAt)
            }
            return out
        }
        return emptyList()
    }

    /** null = format tidak dikenali. */
    fun parseViewers(body: String): ViewerPage? {
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        if (!root.has("viewers") && !root.has("users")) return null
        val arr = root.optJSONArray("viewers")
        val viewers = mutableListOf<Viewer>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                val user = v.optJSONObject("user") ?: continue
                val pk = user.str("pk").ifBlank { user.str("id") }
                val username = user.str("username")
                if (pk.isBlank() || username.isBlank()) continue
                viewers += Viewer(
                    pk = pk,
                    username = username,
                    fullName = user.str("full_name"),
                    picUrl = user.str("profile_pic_url").ifBlank { null },
                    followedBy = user.optJSONObject("friendship_status")?.optBoolean("followed_by", false) ?: false,
                    hasLiked = v.optBoolean("has_liked", false),
                    replied = v.str("reply_text").isNotBlank(),
                    isBestie = user.optJSONObject("friendship_status")?.optBoolean("is_bestie", false) ?: false,
                    isSpam = v.optBoolean("is_spam_viewer", false),
                )
            }
        }
        val next = root.str("next_max_id").ifBlank { null }
        val total = if (root.has("total_viewer_count") && !root.isNull("total_viewer_count")) {
            root.optInt("total_viewer_count", -1).takeIf { it >= 0 }
        } else {
            null
        }
        return ViewerPage(viewers, next, total)
    }

    /** String aman: null / hilang jadi "" (JSONObject.optString mengubah JSON null jadi "null"). */
    private fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name, "")
}