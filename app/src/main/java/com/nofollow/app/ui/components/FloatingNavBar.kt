package com.nofollow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ruang bawah yang harus disisakan layar supaya isi paling bawah tidak tertutup bilah navigasi melayang
 * (di luar inset navigation bar sistem, yang sudah ditangani Scaffold masing-masing layar). 0 kalau bilah tidak tampil.
 * Pakai di contentPadding bawah daftar yang bisa di-scroll.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Tinggi bilah + jarak ke tepi bawah + sedikit napas di atasnya. */
val FloatingNavBarClearance: Dp = 94.dp

private val BarHeight = 66.dp
private const val SelectedWeight = 1.7f

data class NavBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    /** Angka lencana; null atau 0 = tanpa lencana. */
    val badge: Int? = null,
)

/**
 * Bilah navigasi melayang berbentuk pil: item terpilih melebar jadi pil berwarna primary berisi ikon + label,
 * item lain hanya ikon. Pasang di atas konten (Box), rata bawah, dengan navigationBarsPadding dari pemanggil.
 */
@Composable
fun FloatingNavBar(
    items: List<NavBarItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(BarHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(8.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavBarEntry(item = item, selected = item.route == selectedRoute, onClick = { onSelect(item.route) })
            }
        }
    }
}

@Composable
private fun RowScope.NavBarEntry(item: NavBarItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val weight by animateFloatAsState(if (selected) SelectedWeight else 1f, tween(250), label = "navWeight")
    val container by animateColorAsState(if (selected) scheme.primary else Color.Transparent, tween(250), label = "navContainer")
    val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, tween(250), label = "navContent")

    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(container)
            .selectable(
                selected = selected,
                onClick = {
                    // Getaran yang sama dengan ketukan tab di Tracker; ketukan di tab yang sudah terpilih diam.
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
                role = Role.Tab,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgedBox(
                badge = {
                    val count = item.badge
                    if (count != null && count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) }
                },
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.icon,
                    contentDescription = if (selected) null else item.label,
                    tint = content,
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(250)) + expandHorizontally(tween(250)),
                exit = fadeOut(tween(150)) + shrinkHorizontally(tween(250)),
            ) {
                Text(
                    text = item.label,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}