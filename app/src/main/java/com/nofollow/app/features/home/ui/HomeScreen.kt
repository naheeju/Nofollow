package com.nofollow.app.features.home.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nofollow.app.ui.components.AppMessageDialog
import com.nofollow.app.ui.components.GroupPosition
import com.nofollow.app.ui.components.GroupedCardGap
import com.nofollow.app.ui.components.LocalBottomBarInset
import com.nofollow.app.ui.components.ScreenTopBar
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofollow.app.features.home.domain.model.HomeAudience
import com.nofollow.app.features.home.domain.model.HomeMenuBadges
import com.nofollow.app.features.kepo.domain.model.KepoEntry
import com.nofollow.app.features.kepo.ui.KepoCard
import com.nofollow.app.features.kepo.ui.KepoViewModel
import com.nofollow.app.features.tracker.domain.model.TrackerTab
import com.nofollow.app.features.update.domain.model.AppUpdate
import com.nofollow.app.features.update.ui.UpdateCard
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalLocale
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    /** Buka layar Tracker; [TrackerTab] terisi kalau harus langsung ke kategori itu, null = biarkan seperti terakhir. */
    onOpenTracker: (TrackerTab?) -> Unit,
    /** Buka layar Kepo (daftar akun yang like story). */
    onOpenKepo: () -> Unit,
    /** Versi baru yang tersedia (null = sudah terbaru); tampil sebagai kartu pengingat di paling atas. */
    update: AppUpdate?,
    onDownloadUpdate: (AppUpdate) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    kepoViewModel: KepoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kepoState by kepoViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Getaran yang sama dengan ketukan tab di Tracker, tiap kali menu di Beranda membuka Tracker.
    val openTracker: (TrackerTab?) -> Unit = { tab ->
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onOpenTracker(tab)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onScreenStarted()
        kepoViewModel.onHomeStarted()
    }

    // Supaya "Sinkron 5 mnt lalu" ikut berjalan tanpa menunggu data berubah.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000.milliseconds)
            value = System.currentTimeMillis()
        }
    }

    state.message?.let { AppMessageDialog(message = it, onDismiss = viewModel::dismissMessage) }
    kepoState.message?.let { AppMessageDialog(message = it, onDismiss = kepoViewModel::dismissMessage) }

    // Kartu progres baru muncul kalau sinkron lebih dari sebentar, supaya tidak berkedip di sinkron yang langsung dilewati.
    var showSyncCard by remember { mutableStateOf(false) }
    LaunchedEffect(state.syncing) {
        if (state.syncing) {
            delay(1_500.milliseconds)
            showSyncCard = true
        } else {
            showSyncCard = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ScreenTopBar(
                title = "Beranda",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (update != null) {
                item(key = "update-available") {
                    UpdateCard(update = update, onDownload = { onDownloadUpdate(update) })
                }
            }
            item {
                ProfileCard(
                    profile = state.overview?.profile,
                    syncLabel = syncLabel(state.overview?.lastSyncedAt, now),
                    syncing = state.syncing,
                    onSync = {
                        // Getaran yang sama dengan ketukan tab di Tracker.
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        viewModel.syncNow()
                    },
                )
            }
            if (showSyncCard) {
                item(key = "sync-progress") { SyncProgressCard(progress = state.overview?.syncProgress) }
            }
            item {
                AudienceGrid(
                    summary = state.overview?.audience,
                    badges = state.overview?.menuBadges,
                    kepoEntries = kepoState.entries,
                    kepoLoaded = kepoState.loaded,
                    onOpenKepo = onOpenKepo,
                    onOpenTab = { openTracker(it) },
                )
            }
            item { GrowthCard(growth = state.overview?.growth) }
            item {
                ActivitySection(
                    items = state.overview?.recentActivity.orEmpty(),
                    now = now,
                    onViewAll = { openTracker(null) },
                )
            }
        }
    }
}

/**
 * Kepo di kiri (kartu tinggi) dan empat menu di kanan sebagai satu kelompok list bergaya GroupedCard.
 * Semua angka dari data lokal. Badge lama (Prioritas, +N minggu ini, persen mutual) tidak dipakai karena tidak muat.
 * Lencana kecil di ikon tiap menu = data baru (unfollow dst.) sejak menu itu terakhir dilihat di Tracker.
 */
@Composable
private fun AudienceGrid(
    summary: HomeAudience?,
    badges: HomeMenuBadges?,
    kepoEntries: List<KepoEntry>,
    kepoLoaded: Boolean,
    onOpenKepo: () -> Unit,
    onOpenTab: (TrackerTab) -> Unit,
) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KepoCard(
            entries = kepoEntries,
            loaded = kepoLoaded,
            onClick = onOpenKepo,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Column(
            modifier = Modifier.weight(1.25f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(GroupedCardGap),
        ) {
            MenuRow(
                icon = Icons.Outlined.PersonOff,
                label = "Tidak follow balik",
                value = summary?.notFollowingBack,
                position = GroupPosition.FIRST,
                modifier = Modifier.weight(1f),
                badge = badges?.notFollowingBack ?: 0,
                onClick = { onOpenTab(TrackerTab.NOT_FOLLOWING_BACK) },
            )
            MenuRow(
                icon = Icons.Outlined.PersonRemove,
                label = "Unfollowers",
                value = summary?.unfollowers,
                position = GroupPosition.MIDDLE,
                modifier = Modifier.weight(1f),
                badge = badges?.unfollowers ?: 0,
                // Unfollow = kabar buruk, jadi merah; sisanya primary.
                badgeContainer = MaterialTheme.colorScheme.error,
                badgeContent = MaterialTheme.colorScheme.onError,
                onClick = { onOpenTab(TrackerTab.UNFOLLOWERS) },
            )
            MenuRow(
                icon = Icons.Outlined.PersonAdd,
                label = "Fans",
                value = summary?.fans,
                position = GroupPosition.MIDDLE,
                modifier = Modifier.weight(1f),
                badge = badges?.fans ?: 0,
                onClick = { onOpenTab(TrackerTab.FANS) },
            )
            MenuRow(
                icon = Icons.Outlined.SwapHoriz,
                label = "Mutual",
                value = summary?.mutual,
                position = GroupPosition.LAST,
                modifier = Modifier.weight(1f),
                badge = badges?.mutual ?: 0,
                onClick = { onOpenTab(TrackerTab.MUTUAL) },
            )
        }
    }
}

private fun syncLabel(at: Long?, now: Long): String {
    if (at == null) return "Belum pernah sinkron"
    val minutes = (now - at).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "Baru saja disinkron"
        minutes < 60 -> "Sinkron $minutes mnt lalu"
        minutes < 60 * 24 -> "Sinkron ${minutes / 60} jam lalu"
        else -> "Sinkron ${minutes / (60 * 24)} hari lalu"
    }
}