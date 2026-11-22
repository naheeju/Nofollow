package com.nofollow.app.core.common

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Apakah ada layar app yang sedang tampil. Dipakai worker latar belakang untuk memutuskan perlu notifikasi
 * "selesai" atau tidak (kalau app sedang terbuka, hasilnya cukup tampil sebagai dialog).
 */
object AppForeground : Application.ActivityLifecycleCallbacks {
    @Volatile
    var isForeground: Boolean = false
        private set

    private var started = 0

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        started++
        isForeground = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        isForeground = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}