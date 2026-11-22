package com.nofollow.app.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofollow.app.core.common.formatAgo
import com.nofollow.app.core.common.openInstagramProfile
import com.nofollow.app.features.home.domain.model.HomeActivity
import com.nofollow.app.features.home.domain.model.HomeActivityType
import com.nofollow.app.ui.components.EventActionButton
import com.nofollow.app.ui.components.EventActionKind
import com.nofollow.app.ui.components.EventAvatar
import com.nofollow.app.ui.components.EventBadgeKind
import com.nofollow.app.ui.components.GroupPosition
import com.nofollow.app.ui.components.GroupedCard
import com.nofollow.app.ui.components.GroupedCardGap

/** Judul + badge jumlah + "Log lengkap", lalu kartu berisi daftar aktivitas (maks 5 dari use case). */
@Composable
internal fun ActivitySection(
    items: List<HomeActivity>,
    now: Long,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Aktivitas Terbaru",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            if (items.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp).background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) TextButton(onClick = onViewAll) { Text("Lihat Semua") }
        }
        if (items.isEmpty()) {
            GroupedCard(position = GroupPosition.SINGLE) { EmptyActivity() }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedCardGap)) {
                items.forEachIndexed { index, item ->
                    ActivityRow(item = item, now = now, position = GroupPosition.of(index, items.size))
                }
            }
        }
    }
}

/** Belum ada kejadian sama sekali: ikon + penjelasan, di dalam kartu yang sama dengan daftar. */
@Composable
private fun EmptyActivity() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).background(scheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.NotificationsNone,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = scheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Belum ada aktivitas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Follow, unfollow, dan ganti username akan muncul di sini.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Satu kartu per kejadian gaya Pengaturan. Mengetuk kartu atau tombolnya langsung membuka profil di Instagram. */
@Composable
private fun ActivityRow(item: HomeActivity, now: Long, position: GroupPosition) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Akun yang sudah tidak tersedia tidak punya halaman profil untuk dibuka.
    val openProfile: (() -> Unit)? =
        if (item.type != HomeActivityType.UNAVAILABLE) ({ context.openInstagramProfile(item.username) }) else null
    // Kartu bergetar sendiri (GroupedCard); tombol di dalamnya bukan bagian kartu, jadi bergetar di sini.
    val openProfileFromButton: (() -> Unit)? = openProfile?.let { open ->
        {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            open()
        }
    }

    GroupedCard(position = position, onClick = openProfile) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventAvatar(
                avatarPath = item.avatarPath,
                avatarVersion = item.id,
                fallbackLetter = item.username.firstOrNull()?.uppercase(),
                badge = item.type.badgeKind(),
                size = 48.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.describe(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatAgo(item.occurredAt, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (openProfileFromButton != null) {
                    EventActionButton(
                        kind = item.type.actionKind(),
                        iFollow = item.iFollow,
                        onClick = openProfileFromButton,
                    )
                }
            }
        }
    }
}

private fun HomeActivityType.actionKind(): EventActionKind = when (this) {
    HomeActivityType.UNFOLLOW -> EventActionKind.UNFOLLOW
    HomeActivityType.FOLLOW -> EventActionKind.FOLLOW
    HomeActivityType.REFOLLOW -> EventActionKind.REFOLLOW
    else -> EventActionKind.OTHER
}

private fun HomeActivity.describe(): String = when (type) {
    HomeActivityType.UNFOLLOW -> "Berhenti mengikuti kamu"
    HomeActivityType.FOLLOW -> "Mulai mengikuti kamu"
    HomeActivityType.REFOLLOW -> "Mengikuti kamu lagi"
    HomeActivityType.RENAME -> previousUsername?.let { "Ganti username dari @$it" } ?: "Ganti username"
    HomeActivityType.UNAVAILABLE -> "Akun dihapus atau tidak tersedia"
}

private fun HomeActivityType.badgeKind(): EventBadgeKind = when (this) {
    HomeActivityType.UNFOLLOW -> EventBadgeKind.UNFOLLOW
    HomeActivityType.FOLLOW -> EventBadgeKind.FOLLOW
    HomeActivityType.REFOLLOW -> EventBadgeKind.REFOLLOW
    HomeActivityType.RENAME -> EventBadgeKind.RENAME
    HomeActivityType.UNAVAILABLE -> EventBadgeKind.UNAVAILABLE
}