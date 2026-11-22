package com.nofollow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Jenis kejadian untuk lencana di pojok foto (dipetakan dari enum tiap feature). */
enum class EventBadgeKind { UNFOLLOW, FOLLOW, REFOLLOW, RENAME, UNAVAILABLE }

/**
 * Foto akun dengan lencana ikon kecil di pojok kanan bawah, dipakai bersama oleh Beranda dan Tracker.
 * Warna lencana per jenis: unfollow/hilang = error, follow/refollow = primary, rename = tertiary.
 * [ringColor] harus sama dengan warna latar tempat avatar berada supaya cincin pemisah lencana menyatu.
 */
@Composable
fun EventAvatar(
    avatarPath: String?,
    avatarVersion: Long?,
    fallbackLetter: String?,
    badge: EventBadgeKind,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ringColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val style = badge.style(MaterialTheme.colorScheme)
    Box(modifier) {
        ProfileAvatar(
            avatarPath = avatarPath,
            avatarVersion = avatarVersion,
            fallbackLetter = fallbackLetter,
            size = size,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(20.dp)
                .background(ringColor, CircleShape)
                .padding(2.dp)
                .background(style.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                style.icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = style.content,
            )
        }
    }
}

private class BadgeStyle(val icon: ImageVector, val container: Color, val content: Color)

private fun EventBadgeKind.style(scheme: ColorScheme): BadgeStyle = when (this) {
    EventBadgeKind.UNFOLLOW -> BadgeStyle(Icons.Outlined.PersonRemove, scheme.errorContainer, scheme.onErrorContainer)
    EventBadgeKind.UNAVAILABLE -> BadgeStyle(Icons.Outlined.PersonOff, scheme.errorContainer, scheme.onErrorContainer)
    EventBadgeKind.FOLLOW -> BadgeStyle(Icons.Outlined.PersonAdd, scheme.primaryContainer, scheme.onPrimaryContainer)
    EventBadgeKind.REFOLLOW -> BadgeStyle(Icons.Outlined.Replay, scheme.primaryContainer, scheme.onPrimaryContainer)
    EventBadgeKind.RENAME -> BadgeStyle(Icons.Outlined.Edit, scheme.tertiaryContainer, scheme.onTertiaryContainer)
}