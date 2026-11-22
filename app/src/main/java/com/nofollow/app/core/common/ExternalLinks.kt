package com.nofollow.app.core.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Buka [url] di browser. Kalau tidak ada app yang bisa membukanya, tidak terjadi apa-apa. */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Tidak ada browser: tidak ada yang bisa dilakukan.
    }
}