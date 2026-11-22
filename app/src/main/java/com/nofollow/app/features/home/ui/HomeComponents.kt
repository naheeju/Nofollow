package com.nofollow.app.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofollow.app.features.home.domain.model.HomeProfile
import com.nofollow.app.features.home.domain.model.HomeSyncProgress
import com.nofollow.app.ui.components.GroupPosition
import com.nofollow.app.ui.components.GroupedCard
import com.nofollow.app.ui.components.ProfileAvatar

/** Hijau tetap (bukan warna tema) untuk titik status di foto. */
private val StatusGreen = Color(0xFF34C759)

/** Kartu akun: foto, @username, "Sinkron N mnt lalu", dan tombol Sinkron. */
@Composable
internal fun ProfileCard(
    profile: HomeProfile?,
    syncLabel: String,
    syncing: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box {
                ProfileAvatar(
                    avatarPath = profile?.avatarPath,
                    avatarVersion = profile?.avatarVersion,
                    fallbackLetter = profile?.username?.firstOrNull()?.uppercase(),
                    size = 56.dp,
                )
                // Titik status di pojok foto.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(scheme.surfaceContainerLow, CircleShape)
                        .padding(2.dp)
                        .background(StatusGreen, CircleShape),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = profile?.username?.let { "@$it" } ?: "Akun Instagram",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = syncLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalButton(
                onClick = onSync,
                enabled = !syncing,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Sinkron")
            }
        }
    }
}

/** Satu kartu angka: lingkaran ikon + badge opsional di atas, angka besar, lalu label. Bisa diketuk kalau [onClick] diisi. */
@Composable
internal fun StatCard(
    icon: ImageVector,
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    badge: StatBadgeSpec? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // Kartu tanpa onClick tampil sama persis; warna disabled disamakan supaya tidak terlihat pudar.
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLow,
            disabledContainerColor = scheme.surfaceContainerLow,
            disabledContentColor = scheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.primary)
                }
                badge?.let { StatBadge(it.text, it.container, it.content) }
            }
            Spacer(Modifier.size(16.dp))
            Text(
                text = value?.toString() ?: "-",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Isi badge di pojok kartu. */
internal data class StatBadgeSpec(val text: String, val container: Color, val content: Color)

/** Pil kecil di pojok kartu ("Prioritas", "+3 minggu ini", "95,2%"). Selalu satu baris. */
@Composable
internal fun StatBadge(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Kartu keterangan selama sinkron berjalan: tahap yang sedang dikerjakan, bar kemajuan, dan perkiraan waktu.
 * Sinkron pertama (baseline) total pengikutnya belum diketahui, jadi perkiraannya per 100 pengikut dan bar-nya
 * tidak menentu; sinkron berikutnya sudah tahu jumlah pengikut sehingga ada perkiraan menit dan bar terisi.
 */
@Composable
internal fun SyncProgressCard(progress: HomeSyncProgress?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val first = progress?.baselineDone != true

    val title = if (first) "Sinkron pertama sedang berjalan" else "Sinkron sedang berjalan"
    val stage = when {
        progress == null || (first && !progress.sweepActive) -> "Menyiapkan..."
        progress.sweepActive && first ->
            "Membaca pengikut: ${progress.followersRead} terbaca (halaman ${progress.pagesDone})"
        progress.sweepActive -> "Membaca pengikut: halaman ${progress.pagesDone} dari sekitar ${progress.expectedPages}"
        else -> "Membaca daftar following dan Kepo..."
    }
    val note = if (first) {
        // Total pengikut belum diketahui di sinkron pertama (Instagram tidak memberi totalnya), jadi tidak ada
        // perkiraan angka: yang ditampilkan hanya jumlah nyata yang sudah terbaca di baris tahap di atas.
        "Daftar pengikut dibaca dengan jeda acak supaya akunmu tetap aman, jadi butuh waktu: makin banyak pengikut, " +
                "makin lama. Foto profil ikut diunduh supaya daftarnya tidak kosong. " +
                "Boleh ditinggal, sinkron lanjut di latar belakang. Kalau terputus, lanjut dari halaman terakhir."
    } else {
        val minutes = progress.regularMinutes()
        val scope = progress.followingCount?.let { "${progress.followersRead} pengikut dan $it following" }
            ?: "${progress.followersRead} pengikut"
        "Perkiraan ${minutes.first}–${minutes.last} menit untuk $scope, tergantung kecepatan Instagram. " +
                "Boleh ditinggal, sinkron lanjut di latar belakang."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val fraction = progress?.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Satu baris menu (ikon, label, angka) dalam kelompok GroupedCard, untuk kolom kanan di samping kartu Kepo.
 * [modifier] biasanya `Modifier.weight(1f)` supaya keempat baris membagi tinggi kolom sama rata.
 * [badge] = jumlah data baru sejak menu ini terakhir dilihat; lencana kecil di pojok ikon, 0 = tanpa lencana.
 */
@Composable
internal fun MenuRow(
    icon: ImageVector,
    label: String,
    value: Int?,
    position: GroupPosition,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    badgeContainer: Color = MaterialTheme.colorScheme.primary,
    badgeContent: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    GroupedCard(position = position, modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BadgedBox(
                badge = {
                    if (badge > 0) {
                        Badge(containerColor = badgeContainer, contentColor = badgeContent) {
                            Text(if (badge > 99) "99+" else badge.toString())
                        }
                    }
                },
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.primary)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value?.toString() ?: "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}