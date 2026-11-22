package com.nofollow.app.navigation

sealed class AppDestination(val route: String) {
    data object Onboarding : AppDestination("onboarding")
    data object Login : AppDestination("login")
    data object Home : AppDestination("home")
    data object Tracker : AppDestination("tracker")
    data object Settings : AppDestination("settings")

    /** Bukan tab bilah bawah: dibuka dari kartu Kepo di Beranda, back kembali ke Beranda. */
    data object Kepo : AppDestination("kepo")

    companion object {
        /** Tab di bilah navigasi bawah, berurutan. */
        val mainTabs: List<AppDestination> by lazy { listOf(Home, Tracker, Settings) }
    }
}