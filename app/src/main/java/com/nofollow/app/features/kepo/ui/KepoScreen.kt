package com.nofollow.app.features.kepo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofollow.app.core.common.formatAgo
import com.nofollow.app.core.common.openInstagramProfile
import com.nofollow.app.features.kepo.domain.model.KepoEntry
import com.nofollow.app.features.kepo.domain.model.KepoFilter
import com.nofollow.app.features.kepo.domain.model.allList
import com.nofollow.app.features.kepo.domain.model.hero
import com.nofollow.app.features.kepo.domain.model.ranked
import com.nofollow.app.ui.components.AppMessageDialog
import com.nofollow.app.ui.components.EventActionButton
import com.nofollow.app.ui.components.EventActionKind
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

/** Urutan filter = urutan halaman pager. */
private val Filters = KepoFilter.entries

/**
 * Daftar penonton story: kartu "Paling Sering Kepoin Kamu" dan chip filter diam di atas (tidak ikut tergulung),
 * daftar di bawahnya bergulir dan bisa digeser kiri-kanan antar filter seperti tab Tracker, tombol Cek mengambang. Angka "Kepo Nx" = jumlah story yang ditonton orang itu
 * (dihitung sejak fitur ini aktif). Filter "Semua" = yang berinteraksi + 5 penonton teratas. Mengetuk baris membuka profilnya di Instagram.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KepoScreen(
    onBack: () -> Unit,
    viewModel: KepoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Label "Baru" tetap terlihat selama layar terbuka; baru dianggap dilihat saat keluar.
    DisposableEffect(Unit) { onDispose { viewModel.markSeen() } }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000.milliseconds)
            value = System.currentTimeMillis()
        }
    }

    state.message?.let { AppMessageDialog(message = it, onDismiss = viewModel::dismissMessage) }

    val ranked = remember(state.entries) { state.entries.ranked() }
    val hero = remember(state.entries) { state.entries.hero() }
    val everyone = remember(state.entries) { state.entries.allList() }
    val counts = remember(state.entries, everyone) {
        Filters.associateWith { f ->
            if (f == KepoFilter.ALL) everyone.size else state.entries.count { f.matches(it) }
        }
    }
    // Isi tiap filter. Pada filter Semua, orang di kartu atas tidak diulang di daftar.
    fun itemsFor(f: KepoFilter): List<KepoEntry> {
        val base = if (f == KepoFilter.ALL) everyone else ranked.filter { f.matches(it) }
        return if (f == KepoFilter.ALL && hero != null) base.filter { it.pk != hero.pk } else base
    }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = Filters.indexOf(state.filter).coerceAtLeast(0)) { Filters.size }
    val chipsState = rememberLazyListState()
    // true selama animasi pindah halaman akibat ketukan chip; ketukan sudah bergetar sendiri.
    var tapScrolling by remember { mutableStateOf(false) }

    // Filter terpilih di ViewModel mengikuti halaman yang berhenti.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.setFilter(Filters[it]) }
    }
    // Getaran halus tiap kali halaman berganti karena digeser (bukan karena ketukan chip).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { if (!tapScrolling) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }
    // Chip terpilih ikut tergulir ke area yang terlihat.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { chipsState.animateScrollToItem((it - 1).coerceAtLeast(0)) }
    }

    val bottomInset = LocalBottomBarInset.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ScreenTopBar(
                title = "Kepo",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        floatingActionButton = {
            CheckFab(checking = state.checking, onClick = viewModel::checkNow, modifier = Modifier.padding(bottom = bottomInset))
        },
    ) { padding ->
        if (state.loaded && state.entries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Visibility,
                title = "Belum ada yang kepo nichhh",
                subtitle = "Posting dulu, siapa tau ada yang kepo xixixi ^-^",
                // Bar navigasi melayang menutupi bagian bawah: sisihkan supaya titik tengahnya pas di area yang terlihat.
                modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = bottomInset),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Bagian atas diam: kartu teratas dan filter tidak ikut tergulung, hanya daftar di bawahnya.
                if (hero != null) {
                    HeroCard(
                        entry = hero,
                        now = now,
                        onOpen = { context.openInstagramProfile(hero.username) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    )
                }
                LazyRow(
                    state = chipsState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(Filters) { page, f ->
                        FilterChip(
                            selected = pagerState.currentPage == page,
                            onClick = {
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
                            label = { Text("${f.label} (${counts[f] ?: 0})") },
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    key = { Filters[it] },
                ) { page ->
                    val listItems = itemsFor(Filters[page])
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Ruang ekstra di bawah supaya baris terakhir tidak tertutup tombol Cek dan bar navigasi.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset + 88.dp),
                        verticalArrangement = Arrangement.spacedBy(GroupedCardGap),
                    ) {
                        if (listItems.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Rounded.Inbox,
                                    title = "Belum ada yang cocok",
                                    subtitle = "Belum ada yang kepo di sini :(",
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }
                        itemsIndexed(listItems, key = { _, e -> e.pk }) { index, entry ->
                            KepoRow(
                                entry = entry,
                                position = GroupPosition.of(index, listItems.size),
                                now = now,
                                onOpen = { context.openInstagramProfile(entry.username) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tombol Cek mengambang; berputar selama pengecekan berjalan. */
@Composable
private fun CheckFab(checking: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = { if (!checking) onClick() },
        modifier = modifier,
        icon = {
            if (checking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
        },
        text = { Text(if (checking) "Ngecek\u2026" else "Cek") },
    )
}

/**
 * Keadaan kosong, tampilannya sama dengan tab Tracker: ikon dalam lingkaran, judul, dan keterangan di tengah area daftar.
 */
@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
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

/** Kartu atas: orang dengan peringkat tertinggi (interaksi dulu, lalu jumlah story ditonton). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(entry: KepoEntry, now: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).background(scheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.error)
                }
                Text(
                    "Paling Sering Kepoin Kamu",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                KepoTag("Top #1", KepoTagStyle.NEUTRAL)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.border(2.dp, scheme.primary, CircleShape).padding(3.dp)) {
                    ProfileAvatar(
                        avatarPath = entry.avatarPath,
                        avatarVersion = null,
                        fallbackLetter = entry.username.firstOrNull()?.uppercase(),
                        size = 56.dp,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "@${entry.username}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.summary(now),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        entry.tags().forEach { KepoTag(it.first, it.second) }
                    }
                }
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Intip Balik")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KepoRow(entry: KepoEntry, position: GroupPosition, now: Long, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    GroupedCard(position = position, onClick = onOpen) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = entry.fullName.ifBlank { entry.username },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.isNew) NewBadge("Baru")
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
                    text = entry.timeLine(now),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entry.tags().forEach { KepoTag(it.first, it.second) }
                }
            }
            EventActionButton(kind = EventActionKind.OTHER, iFollow = null, onClick = onOpen)
        }
    }
}

private enum class KepoTagStyle { NEUTRAL, GOOD, WARM, ACCENT }

@Composable
private fun KepoTag(text: String, style: KepoTagStyle) {
    val scheme = MaterialTheme.colorScheme
    val (container: Color, content: Color) = when (style) {
        KepoTagStyle.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        KepoTagStyle.GOOD -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KepoTagStyle.WARM -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        KepoTagStyle.ACCENT -> scheme.primary to scheme.onPrimary
    }
    Box(modifier = Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Chip per orang. Angkanya dari catatan nyata, kata-katanya yang dibuat gimmick:
 * "Kepo Nx" (jumlah story yang ditonton), like / balasan, status follow, lalu satu label (Secret menang atas Ghost).
 */
private fun KepoEntry.tags(): List<Pair<String, KepoTagStyle>> = buildList {
    if (watchCount > 0) add("Kepo ${watchCount}x" to KepoTagStyle.ACCENT)
    if (likeCount > 0) add("Naksir ${likeCount}x" to KepoTagStyle.WARM)
    if (replyCount > 0) add("Balas ${replyCount}x" to KepoTagStyle.WARM)
    add(
        when {
            followedBy && iFollow == true -> "Mutual" to KepoTagStyle.GOOD
            followedBy -> "Follower" to KepoTagStyle.GOOD
            else -> "Bukan follower" to KepoTagStyle.NEUTRAL
        },
    )
    when {
        isSecretAdmirer -> add("Secret Admirer" to KepoTagStyle.WARM)
        isGhostAdmirer -> add("Ghost Admirer" to KepoTagStyle.WARM)
    }
}

private fun KepoEntry.timeLine(now: Long): String =
    if (interactionCount > 0) "Terakhir kepo ${formatAgo(lastAt, now)}" else "Pertama kali kepo ${formatAgo(lastAt, now)}"

/** Ringkasan satu kalimat di kartu atas: "Kepo 3x · 2 naksir · 25 mnt lalu". */
private fun KepoEntry.summary(now: Long): String = buildList {
    if (watchCount > 0) add("Kepo ${watchCount}x")
    if (likeCount > 0) add("$likeCount naksir")
    if (replyCount > 0) add("$replyCount balasan")
    add(formatAgo(lastAt, now))
}.joinToString(" · ")