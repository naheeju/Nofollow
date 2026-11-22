package com.nofollow.app.features.kepo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nofollow.app.features.kepo.domain.model.KepoEntry
import com.nofollow.app.features.kepo.domain.model.allList
import com.nofollow.app.features.kepo.domain.model.cardFaces
import com.nofollow.app.ui.components.GroupPosition
import com.nofollow.app.ui.components.GroupedCard
import com.nofollow.app.ui.components.ProfileAvatar

/**
 * Kartu Kepo di Beranda (kolom kiri, setinggi daftar menu di kanannya; gaya GroupedCard seperti Pengaturan):
 * ikon hati + chip jumlah di atas, judul "Kepo" + kalimat gimmick di tengah (plus 5 foto menjajar: yang berinteraksi dulu,
 * sisanya penonton teratas story terbaru),
 * tombol "Cek sekarang" di bawah. Pemanggil mengatur lebar dan tinggi lewat [modifier]. Mengetuk kartu membuka layar Kepo.
 */
@Composable
fun KepoCard(
    entries: List<KepoEntry>,
    loaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val newCount = entries.count { it.isNew }
    val hotCount = entries.count { it.interactionCount > 0 }
    val total = remember(entries) { entries.allList().size }
    val faces = remember(entries) { entries.cardFaces() }
    GroupedCard(position = GroupPosition.SINGLE, modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.error)
                }
                if (loaded && total > 0) KepoChip("$total orang", scheme.secondaryContainer, scheme.onSecondaryContainer)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kepo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (newCount > 0) KepoChip("$newCount baru", scheme.primary, scheme.onPrimary)
                }
                Text(
                    text = when {
                        !loaded -> " "
                        faces.isEmpty() -> "Belum ada yang kepo. Posting dulu, siapa tahu \uD83D\uDC40"
                        newCount > 0 -> "Cieee, ada yang kepo nih sama akunmu \uD83D\uDC40"
                        hotCount > 0 -> "Yang pernah kepo sama akunmu \uD83D\uDC40"
                        else -> "Lagi pada kepo sama akunmu \uD83D\uDC40"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                )
                if (faces.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    // Lima foto menjajar bertumpang 10dp (total sekitar 100dp, muat di kolom kiri yang sempit),
                    // dengan cincin warna kartu supaya tepinya jelas.
                    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.CenterVertically) {
                        faces.forEach { e ->
                            Box(
                                modifier = Modifier.size(28.dp).background(scheme.surfaceContainerLow, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                ProfileAvatar(
                                    avatarPath = e.avatarPath,
                                    avatarVersion = null,
                                    fallbackLetter = e.username.firstOrNull()?.uppercase(),
                                    size = 24.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KepoChip(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Dipakai juga oleh layar Kepo untuk label "Baru". */
@Composable
internal fun NewBadge(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(scheme.primary, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = scheme.onPrimary, fontWeight = FontWeight.Medium)
    }
}