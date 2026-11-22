package com.nofollow.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Jenis kejadian untuk tombol aksi (dipetakan dari enum tiap feature). */
enum class EventActionKind { UNFOLLOW, FOLLOW, REFOLLOW, OTHER }

/**
 * Tombol aksi di kanan baris kejadian. Label dan warna mengikuti jenis kejadian dan apakah kamu mengikuti akun itu
 * ([iFollow], dari daftar following tersimpan; null = belum diketahui, diperlakukan sebagai "tidak"):
 * - UNFOLLOW dan kamu mengikutinya: "Unfoll", merah M3 (error). Kalau tidak: "Profil".
 * - FOLLOW: kamu mengikutinya = "Mutual", kalau tidak = "Follow" (primary).
 * - REFOLLOW: kamu mengikutinya = "Mutual", kalau tidak = "Profil".
 * - lainnya: "Profil".
 * "Profil" abu-abu netral M3, "Mutual" tonal (secondaryContainer). [checked] menambah centang di depan label.
 * Tombol hanya membuka profil di app Instagram; app ini tidak melakukan follow/unfollow sendiri.
 */
@Composable
fun EventActionButton(
    kind: EventActionKind,
    iFollow: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val padding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    val sized = modifier.height(32.dp)
    val following = iFollow == true
    val labelStyle = MaterialTheme.typography.labelLarge

    @Composable
    fun Label(text: String) {
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = "Sudah dicek", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = labelStyle)
    }

    when {
        kind == EventActionKind.UNFOLLOW && following -> Button(
            onClick = onClick,
            modifier = sized,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
        ) { Label("Unfoll") }

        kind == EventActionKind.FOLLOW && !following -> Button(
            onClick = onClick,
            modifier = sized,
            contentPadding = padding,
        ) { Label("Follow") }

        (kind == EventActionKind.FOLLOW || kind == EventActionKind.REFOLLOW) && following -> FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            contentPadding = padding,
        ) { Label("Mutual") }

        else -> FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurfaceVariant,
            ),
        ) { Label("Profil") }
    }
}