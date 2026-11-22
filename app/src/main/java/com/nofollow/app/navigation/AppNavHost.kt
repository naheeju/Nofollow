package com.nofollow.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nofollow.app.core.common.openUrl
import com.nofollow.app.features.auth.ui.LoginScreen
import com.nofollow.app.features.home.ui.HomeScreen
import com.nofollow.app.features.kepo.ui.KepoScreen
import com.nofollow.app.features.onboarding.ui.OnboardingScreen
import com.nofollow.app.features.setting.ui.SettingsScreen
import com.nofollow.app.features.tracker.domain.model.TrackerTab
import com.nofollow.app.features.tracker.ui.TrackerLogScreen
import com.nofollow.app.features.update.ui.UpdatePromptDialog
import com.nofollow.app.features.update.ui.UpdateViewModel
import com.nofollow.app.ui.components.FloatingNavBar
import com.nofollow.app.ui.components.FloatingNavBarClearance
import com.nofollow.app.ui.components.LocalBottomBarInset
import com.nofollow.app.ui.components.NavBarItem
import org.koin.androidx.compose.koinViewModel

private fun navItems(trackerBadge: Int) = listOf(
    NavBarItem(AppDestination.Home.route, "Beranda", Icons.Outlined.Home, Icons.Filled.Home),
    NavBarItem(
        AppDestination.Tracker.route, "Tracker", Icons.Outlined.PersonSearch, Icons.Filled.PersonSearch,
        badge = trackerBadge,
    ),
    NavBarItem(AppDestination.Settings.route, "Pengaturan", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AppNavHost(
    viewModel: AppViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel(),
) {
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val trackerBadge by viewModel.trackerBadge.collectAsStateWithLifecycle()
    val updatePrompt by updateViewModel.prompt.collectAsStateWithLifecycle()
    val availableUpdate by updateViewModel.available.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (gate is AuthGate.Loading) return

    val navController = rememberNavController()
    val start = when (gate) {
        is AuthGate.LoggedIn -> AppDestination.Home
        AuthGate.Onboarding -> AppDestination.Onboarding
        else -> AppDestination.Login
    }
    val mainRoutes = AppDestination.mainTabs.map { it.route }

    // Kategori Tracker yang diminta Beranda; dibersihkan Tracker setelah dipakai.
    var pendingTab by remember { mutableStateOf<TrackerTab?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = gate is AuthGate.LoggedIn && currentRoute in mainRoutes

    // Selama Tracker tampil, kejadian baru (termasuk yang masuk saat sedang dilihat) langsung dianggap sudah dilihat.
    LaunchedEffect(currentRoute, trackerBadge) {
        if (currentRoute == AppDestination.Tracker.route && trackerBadge > 0) viewModel.markTrackerSeen()
    }

    LaunchedEffect(gate) {
        val loggedIn = gate is AuthGate.LoggedIn
        val target = when (gate) {
            is AuthGate.LoggedIn -> AppDestination.Home
            AuthGate.Onboarding -> AppDestination.Onboarding
            else -> AppDestination.Login
        }
        val current = navController.currentDestination?.route
        // Tab lain sudah halaman utama: user yang sedang di sana tidak ditarik balik ke Beranda.
        val onMainTab = loggedIn && current in mainRoutes
        if (current != target.route && !onMainTab) {
            navController.navigate(target.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun openTab(route: String) {
        navController.navigate(route) {
            // Semua tab menumpuk di atas Beranda; state tiap tab (posisi scroll dll.) disimpan.
            popUpTo(AppDestination.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomBarInset provides if (showBar) FloatingNavBarClearance else 0.dp) {
            NavHost(navController = navController, startDestination = start.route) {
                composable(AppDestination.Onboarding.route) {
                    OnboardingScreen(
                        onFinish = {
                            // Tandai sudah dilihat, lalu langsung ke Login. Navigasi manual (bukan menunggu gate) karena
                            // saat Onboarding dibuka lagi dari Login, flag-nya sudah true dan gate tidak berubah.
                            viewModel.finishOnboarding()
                            navController.navigate(AppDestination.Login.route) {
                                popUpTo(AppDestination.Onboarding.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(AppDestination.Login.route) {
                    // Back dari Login kembali ke pengenalan, bukan menutup app. Dari pengenalan, back di halaman
                    // pertama baru keluar app.
                    BackHandler {
                        navController.navigate(AppDestination.Onboarding.route) {
                            popUpTo(AppDestination.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    LoginScreen()
                }
                composable(AppDestination.Home.route) {
                    HomeScreen(
                        onOpenTracker = { tab ->
                            pendingTab = tab
                            openTab(AppDestination.Tracker.route)
                        },
                        onOpenKepo = {
                            navController.navigate(AppDestination.Kepo.route) { launchSingleTop = true }
                        },
                        update = availableUpdate,
                        onDownloadUpdate = { context.openUrl(it.downloadUrl) },
                    )
                }
                composable(AppDestination.Kepo.route) {
                    KepoScreen(onBack = { navController.popBackStack() })
                }
                composable(AppDestination.Tracker.route) {
                    TrackerLogScreen(requestedTab = pendingTab, onRequestHandled = { pendingTab = null })
                }
                composable(AppDestination.Settings.route) {
                    SettingsScreen(onLogout = viewModel::logout)
                }
            }
        }

        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FloatingNavBar(
                items = remember(trackerBadge) { navItems(trackerBadge) },
                selectedRoute = currentRoute,
                onSelect = ::openTab,
                modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp),
            )
        }

        // Pembaruan tersedia: dialog sekali per versi ("Nanti" menutupnya untuk versi itu; pengingat tetap di Beranda).
        updatePrompt?.let { update ->
            UpdatePromptDialog(
                update = update,
                onDownload = {
                    context.openUrl(update.downloadUrl)
                    updateViewModel.dismiss(update.version)
                },
                onLater = { updateViewModel.dismiss(update.version) },
            )
        }
    }
}