package com.nofollow.app.core.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

private const val INSTAGRAM_PACKAGE = "com.instagram.android"

/**
 * Buka profil [username] di app Instagram. App ini sengaja tidak melakukan aksi tulis (follow/unfollow) ke Instagram:
 * semua aksi dikerjakan klien resmi. Kalau app Instagram tidak ada, jatuh ke tautan web (browser).
 */
fun Context.openInstagramProfile(username: String) {
    val name = Uri.encode(username)
    val app = Intent(Intent.ACTION_VIEW, "instagram://user?username=$name".toUri())
        .setPackage(INSTAGRAM_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(app)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(Intent.ACTION_VIEW, "https://www.instagram.com/$name/".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(web)
        } catch (_: ActivityNotFoundException) {
            // Tidak ada app yang bisa membuka tautan: tidak ada yang bisa dilakukan.
        }
    }
}