package com.nofollow.app.features.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofollow.app.core.common.formatAgo
import com.nofollow.app.core.common.openInstagramProfile
import com.nofollow.app.features.tracker.domain.model.AudienceEntry
import com.nofollow.app.features.tracker.domain.model.EventKind
import com.nofollow.app.features.tracker.domain.model.TrackerLogEntry
import com.nofollow.app.features.tracker.domain.model.TrackerTab
import com.nofollow.app.ui.components.EventActionButton
import com.nofollow.app.ui.components.EventActionKind
import com.nofollow.app.ui.components.EventAvatar
import com.nofollow.app.ui.components.EventBadgeKind
import com.nofollow.app.ui.components.GroupPosition
import com.nofollow.app.ui.components.GroupedCard
import com.nofollow.app.ui.components.GroupedCardGap
import com.nofollow.app.ui.components.LocalBottomBarInset
import com.nofollow.app.ui.components.ProfileAvatar
import com.nofollow.app.ui.components.ScreenTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/** Urutan kategori di baris filter. */
private val Tabs = TrackerTab.entries

/** Berapa lama sebuah kategori harus tampil sebelum lencananya dianggap sudah dilihat. */
private const val TabSeenDwellMs = 800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerLogScreen(
    /** Kategori yang diminta dari luar layar ini (mis. ketukan kartu di Beranda); null = tidak ada permintaan. */
    requestedTab: TrackerTab? = null,
    onRequestHandled: () -> Unit = {},
    viewModel: TrackerLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Supaya "2 jam lalu" ikut berjalan tanpa menunggu data berubah.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000.milliseconds)
            value = System.currentTimeMillis()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState(initialPage = Tabs.indexOf(state.filter).coerceAtLeast(0)) { Tabs.size }
    val chipsState = rememberLazyListState()

    // true selama animasi pindah halaman akibat ketukan tab; ketukan sudah bergetar sendiri.
    var tapScrolling by remember { mutableStateOf(false) }

    // Kategori terpilih di ViewModel mengikuti halaman yang berhenti.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.onFilterSelected(Tabs[it]) }
    }
    // Getaran halus tiap kali halaman berganti karena digeser (bukan karena ketukan tab).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { if (!tapScrolling) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }
    // Chip terpilih ikut tergulir ke area yang terlihat.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { chipsState.animateScrollToItem((it - 1).coerceAtLeast(0)) }
    }

    // Kategori yang tampil dianggap sudah dilihat (lencana di menu Beranda hilang) setelah sebentar, bukan seketika:
    // layar ini sempat menampilkan kategori terakhir sebelum lompat ke kategori yang diminta Beranda, dan itu belum "dilihat".
    // Data baru yang masuk selama kategori ini tampil ikut dianggap dilihat (kunci [seenBadge] berubah, hitung ulang).
    val visibleTab = Tabs[pagerState.settledPage]
    val seenBadge = badges.of(visibleTab)
    LaunchedEffect(visibleTab, seenBadge) {
        if (seenBadge > 0) {
            delay(TabSeenDwellMs.milliseconds)
            viewModel.onTabViewed(visibleTab)
        }
    }

    // Permintaan pindah kategori dari Beranda: langsung lompat (layar baru dibuka), tanpa getaran halaman.
    LaunchedEffect(requestedTab) {
        val tab = requestedTab ?: return@LaunchedEffect
        val page = Tabs.indexOf(tab)
        if (page != pagerState.currentPage) {
            tapScrolling = true
            try {
                pagerState.scrollToPage(page)
            } finally {
                tapScrolling = false
            }
        }
        onRequestHandled()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { ScreenTopBar(title = "Tracker", scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            FilterRow(
                selectedPage = pagerState.currentPage,
                counts = state.counts,
                listState = chipsState,
                onClick = { page ->
                    if (page != pagerState.currentPage) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        scope.launch {
                            tapScrolling = true
                            try {
                                pagerState.animateScrollToPage(page)
                            } finally {
                                tapScrolling = false
                            }
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { Tabs[it] },
            ) { page ->
                val tab = Tabs[page]
                val kind = tab.kind
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current),
                    verticalArrangement = Arrangement.spacedBy(GroupedCardGap),
                ) {
                    if (kind != null) {
                        val items = state.itemsByKind[kind].orEmpty()
                        if (state.loaded && items.isEmpty()) {
                            item { EmptyState(query = state.query, tab = tab, modifier = Modifier.fillParentMaxSize()) }
                        }
                        itemsIndexed(items, key = { _, entry -> entry.id }) { index, entry ->
                            val checked = entry.checkKey in state.checkedKeys
                            LogRow(
                                entry = entry,
                                position = GroupPosition.of(index, items.size),
                                now = now,
                                checked = checked,
                                onAction = {
                                    viewModel.markChecked(entry)
                                    context.openInstagramProfile(entry.username)
                                },
                                onToggleChecked = { viewModel.toggleChecked(entry, checked) },
                            )
                        }
                    } else {
                        // Tab turunan (tidak follow balik, fans, mutual): daftar akun, null = following belum diambil.
                        val list: List<AudienceEntry>? = when (tab) {
                            TrackerTab.NOT_FOLLOWING_BACK -> state.notFollowingBack
                            TrackerTab.FANS -> state.fans
                            else -> state.mutual
                        }
                        if (state.loaded && list.isNullOrEmpty()) {
                            item {
                                EmptyState(
                                    query = state.query,
                                    tab = tab,
                                    followingMissing = list == null,
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }
                        val rows = list.orEmpty()
                        itemsIndexed(rows, key = { _, entry -> entry.pk }) { index, entry ->
                            AccountRow(
                                entry = entry,
                                tab = tab,
                                position = GroupPosition.of(index, rows.size),
                                onAction = { context.openInstagramProfile(entry.username) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // Teks dipegang lokal: state dari ViewModel melewati combine + flowOn(Default), jadi baliknya telat dan
    // membuat kursor lompat / huruf tertukar saat mengetik cepat. ViewModel cukup diberi tahu, tidak dibaca balik.
    var text by rememberSaveable { mutableStateOf(query) }
    val change: (String) -> Unit = {
        text = it
        onQueryChange(it)
    }
    TextField(
        value = text,
        onValueChange = change,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text("Cari username atau nama") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { change("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            disabledContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

/** Tab kategori berbentuk pil (radius penuh, sama besar di semua sudut). Terpilih mengikuti halaman pager. */
@Composable
private fun FilterRow(
    selectedPage: Int,
    counts: Map<TrackerTab, Int>,
    listState: LazyListState,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(Tabs) { index, tab ->
            val isSelected = index == selectedPage
            FilterChip(
                selected = isSelected,
                onClick = { onClick(index) },
                shape = CircleShape,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tab.label())
                        CountBadge(counts[tab] ?: 0)
                    }
                },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/** Lencana jumlah: latar primary, angka onPrimary. */
@Composable
private fun CountBadge(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).background(scheme.primary, CircleShape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onPrimary,
        )
    }
}

/**
 * Keadaan kosong: ikon dalam lingkaran, judul, dan keterangan, semuanya di tengah area daftar.
 * Dipakai untuk hasil pencarian kosong, daftar following yang belum diambil (null), dan kategori yang masih kosong.
 */
@Composable
private fun EmptyState(
    query: String,
    tab: TrackerTab,
    modifier: Modifier = Modifier,
    followingMissing: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val icon: ImageVector
    val title: String
    val subtitle: String
    when {
        query.isNotBlank() -> {
            icon = Icons.Rounded.SearchOff
            title = "Tidak ada hasil"
            subtitle = "Tidak ada yang cocok dengan \"${query.trim()}\". Coba kata kunci lain."
        }
        followingMissing -> {
            icon = Icons.Rounded.CloudDownload
            title = "Following belum diambil"
            subtitle = "Sinkron dulu dari Beranda supaya daftar tidak follow balik, fans, dan mutual bisa dihitung."
        }
        tab == TrackerTab.NOT_FOLLOWING_BACK -> {
            icon = Icons.Rounded.CheckCircle
            title = "Semua follow balik"
            subtitle = "Semua akun yang kamu ikuti sudah mengikuti kamu balik."
        }
        tab == TrackerTab.FANS -> {
            icon = Icons.Rounded.Groups
            title = "Belum ada fans"
            subtitle = "Semua pengikutmu sudah kamu ikuti juga."
        }
        tab == TrackerTab.MUTUAL -> {
            icon = Icons.Rounded.SwapHoriz
            title = "Belum ada mutual"
            subtitle = "Belum ada akun yang saling follow denganmu."
        }
        else -> {
            icon = Icons.Rounded.Inbox
            title = "Belum ada catatan"
            subtitle = "Belum ada yang tercatat di kategori ${tab.label()}."
        }
    }
    Box(modifier = modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(72.dp).background(scheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = scheme.primary)
            }
            Text(
                text = title,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Baris akun untuk tab turunan: foto, nama + @username, keterangan sesuai tab, dan tombol yang membuka profilnya.
 * Tidak follow balik = "Unfoll" (merah), Fans = "Follow", Mutual = "Mutual".
 */
@Composable
private fun AccountRow(
    entry: AudienceEntry,
    tab: TrackerTab,
    position: GroupPosition,
    onAction: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    GroupedCard(position = position, onClick = onAction) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileAvatar(
                avatarPath = entry.avatarPath,
                avatarVersion = null,
                fallbackLetter = entry.username.firstOrNull()?.uppercase(),
                size = 48.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entry.fullName.isNotBlank()) {
                    Text(
                        text = entry.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "@${entry.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tab.accountCaption(),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            when (tab) {
                TrackerTab.FANS -> EventActionButton(kind = EventActionKind.FOLLOW, iFollow = false, onClick = onAction)
                TrackerTab.MUTUAL -> EventActionButton(kind = EventActionKind.FOLLOW, iFollow = true, onClick = onAction)
                else -> EventActionButton(kind = EventActionKind.UNFOLLOW, iFollow = true, onClick = onAction)
            }
        }
    }
}

private fun TrackerTab.accountCaption(): String = when (this) {
    TrackerTab.FANS -> "Mengikuti kamu, belum kamu follback"
    TrackerTab.MUTUAL -> "Saling follow"
    else -> "Tidak mengikuti kamu balik"
}

/**
 * Satu kartu per kejadian, gayanya sama dengan Pengaturan dan Beranda. Isi: foto, nama + @username + keterangan,
 * lalu waktu dan tombol aksi di kanan. Mengetuk kartu atau tombol membuka profil di Instagram dan menandai kejadian
 * sudah dicek (centang); tekan lama kartu untuk menandai/membatalkan tanda manual.
 */
@Composable
private fun LogRow(
    entry: TrackerLogEntry,
    position: GroupPosition,
    now: Long,
    checked: Boolean,
    onAction: () -> Unit,
    onToggleChecked: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Kartu dan tombolnya sama-sama membuka profil; tekan lama kartu untuk menandai/membatalkan tanda dicek.
    GroupedCard(
        position = position,
        onClick = if (entry.kind != EventKind.UNAVAILABLE) onAction else null,
        onLongClick = onToggleChecked,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(if (checked) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventAvatar(
                avatarPath = entry.avatarPath,
                avatarVersion = entry.id,
                fallbackLetter = entry.username.firstOrNull()?.uppercase(),
                badge = entry.kind.badgeKind(),
                size = 48.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entry.fullName.isNotBlank()) {
                    Text(
                        text = entry.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "@${entry.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.describe(),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatAgo(entry.occurredAt, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                // Akun yang sudah tidak tersedia tidak punya halaman profil untuk dibuka.
                if (entry.kind != EventKind.UNAVAILABLE) {
                    EventActionButton(
                        kind = entry.kind.actionKind(),
                        iFollow = entry.iFollow,
                        checked = checked,
                        onClick = onAction,
                    )
                }
            }
        }
    }
}

private fun TrackerTab.label(): String = when (this) {
    TrackerTab.UNFOLLOWERS -> "Unfollowers"
    TrackerTab.NOT_FOLLOWING_BACK -> "Tidak follow balik"
    TrackerTab.FANS -> "Fans"
    TrackerTab.MUTUAL -> "Mutual"
    TrackerTab.FOLLOW -> "Mengikuti"
    TrackerTab.REFOLLOW -> "Mengikuti lagi"
    TrackerTab.RENAME -> "Ganti nama"
    TrackerTab.UNAVAILABLE -> "Hilang"
}

private fun EventKind.badgeKind(): EventBadgeKind = when (this) {
    EventKind.UNFOLLOW -> EventBadgeKind.UNFOLLOW
    EventKind.FOLLOW -> EventBadgeKind.FOLLOW
    EventKind.REFOLLOW -> EventBadgeKind.REFOLLOW
    EventKind.RENAME -> EventBadgeKind.RENAME
    EventKind.UNAVAILABLE -> EventBadgeKind.UNAVAILABLE
}

private fun EventKind.actionKind(): EventActionKind = when (this) {
    EventKind.UNFOLLOW -> EventActionKind.UNFOLLOW
    EventKind.FOLLOW -> EventActionKind.FOLLOW
    EventKind.REFOLLOW -> EventActionKind.REFOLLOW
    else -> EventActionKind.OTHER
}

private fun TrackerLogEntry.describe(): String = when (kind) {
    EventKind.UNFOLLOW -> "Berhenti mengikuti kamu"
    EventKind.FOLLOW -> "Mulai mengikuti kamu"
    EventKind.REFOLLOW -> "Mengikuti kamu lagi"
    EventKind.RENAME -> previousUsername?.let { "Ganti username dari @$it" } ?: "Ganti username"
    EventKind.UNAVAILABLE -> "Akun dihapus atau tidak tersedia"
}