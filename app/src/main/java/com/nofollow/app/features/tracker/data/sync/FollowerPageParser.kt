package com.nofollow.app.features.tracker.data.sync

import org.json.JSONObject

object FollowerPageParser {
    data class Page(val users: List<RemoteFollower>, val nextMaxId: String?)

    /** Null kalau bentuk respons tidak dikenali (jangan dianggap "daftar kosong"). */
    fun parseFollowers(body: String): Page? = try {
        val json = JSONObject(body)
        val arr = json.optJSONArray("users") ?: return null
        val users = ArrayList<RemoteFollower>(arr.length())
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val pk = u.optString("pk").ifBlank { u.optString("id") }
            val username = u.optString("username")
            if (pk.isBlank() || username.isBlank()) continue
            users += RemoteFollower(
                pk = pk,
                username = username,
                fullName = u.optString("full_name"),
                isPrivate = u.optBoolean("is_private"),
                isVerified = u.optBoolean("is_verified"),
                picUrl = u.optString("profile_pic_url").ifBlank { null },
            )
        }
        Page(users, json.optString("next_max_id").ifBlank { null })
    } catch (_: Exception) {
        null
    }

    /** Null kalau field followed_by tidak ada / respons rusak. */
    fun parseFollowedBy(body: String): Boolean? = try {
        val json = JSONObject(body)
        if (json.has("followed_by")) json.getBoolean("followed_by") else null
    } catch (_: Exception) {
        null
    }
}