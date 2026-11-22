package com.nofollow.app.features.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4
private const val LAST_PAGE = PAGE_COUNT - 1

/**
 * Pengenalan app, tampil sekali sebelum login pertama. Empat halaman geser: sambutan, menu, cara kerja yang
 * menjaga akun, lalu penjelasan login dan data. Isi halaman terakhir harus selalu sesuai kenyataan kode
 * (SessionStore, FollowingStore, Room, LoginScreen); ubah teksnya kalau cara app menyimpan data berubah.
 *
 * [onFinish] dipanggil dari tombol terakhir maupun "Lewati".
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val isLast = pager.currentPage == LAST_PAGE

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.surface))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Lewati disembunyikan di halaman terakhir (di sana tombol utamanya sudah "Masuk"), tapi ruangnya tetap
            // supaya isi halaman tidak melompat.
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (!isLast) {
                    TextButton(onClick = onFinish) { Text("Lewati") }
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> MenuPage()
                        2 -> SafetyPage()
                        else -> PrivacyPage()
                    }
                }
            }

            PageDots(current = pager.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    if (isLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
            ) {
                if (isLast) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isLast) "Masuk dengan Instagram" else "Lanjut", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------ halaman

@Composable
private fun WelcomePage() {
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        HeroBadge(Icons.Outlined.PersonRemove)
        FloatingChip("Unfollow", Icons.Outlined.PersonRemove, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 28.dp), delayMs = 0)
        FloatingChip("Fans", Icons.Outlined.PersonAdd, Modifier.align(Alignment.TopEnd).padding(end = 0.dp, top = 56.dp), delayMs = 500)
        FloatingChip("Mutual", Icons.Outlined.SwapHoriz, Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 36.dp), delayMs = 900)
    }
    Spacer(Modifier.height(24.dp))
    PageTitle("Tahu siapa yang unfollow, tanpa drama")
    Spacer(Modifier.height(12.dp))
    PageBody("Nofollow memantau follower Instagram-mu dan memberi tahu begitu ada yang pergi, plus siapa yang belum kamu follow balik.")
}

@Composable
private fun MenuPage() {
    PageTitle("Semua yang kamu butuh, satu app")
    Spacer(Modifier.height(20.dp))
    val rows = listOf(
        listOf(
            Feature(Icons.Outlined.Home, "Beranda", "Pertumbuhan akun dan tombol Sinkron"),
            Feature(Icons.Outlined.PersonRemove, "Unfollowers", "Siapa yang pergi dan kapan"),
        ),
        listOf(
            Feature(Icons.Outlined.PersonOff, "Tidak follow balik", "Yang kamu ikuti, tapi tidak mengikutimu"),
            Feature(Icons.Outlined.PersonAdd, "Fans", "Mengikutimu, belum kamu ikuti"),
        ),
        listOf(
            Feature(Icons.Outlined.SwapHoriz, "Mutual", "Saling follow"),
            Feature(Icons.Outlined.Visibility, "Kepo", "Siapa yang sering kepoin akun kamu"),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { FeatureCard(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun SafetyPage() {
    HeroBadge(Icons.Outlined.Shield, size = 112)
    Spacer(Modifier.height(24.dp))
    PageTitle("Pelan, acak, dan menjaga akunmu")
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoRow(Icons.Outlined.Sync, "Sinkron otomatis", "Jalan sendiri di latar belakang dan memberi notifikasi saat ada yang unfollow.")
        InfoRow(Icons.Outlined.Shield, "Tidak seperti bot", "Jeda acak antar request, plafon harian, dan cooldown otomatis kalau Instagram membatasi.")
        InfoRow(Icons.Outlined.Bedtime, "Jam tenang", "Tidak ada sinkron otomatis tengah malam. Tombol Sinkron manual tetap bisa kapan saja.")
    }
}

@Composable
private fun PrivacyPage() {
    HeroBadge(Icons.Outlined.Lock, size = 112)
    Spacer(Modifier.height(24.dp))
    PageTitle("Login aman, data tetap di HP-mu")
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoRow(
            Icons.Outlined.VerifiedUser, "Halaman login asli Instagram",
            "Kamu masuk lewat instagram.com. Password diketik langsung di sana; app tidak membaca atau menyimpannya.",
        )
        InfoRow(
            Icons.Outlined.CloudOff, "Tanpa server Nofollow",
            "App berkomunikasi dengan Instagram, dan dengan github.com hanya untuk mengecek versi baru (bisa dimatikan di Pengaturan). Tidak ada data kamu yang dikirim ke server kami atau pihak lain.",
        )
        InfoRow(
            Icons.Outlined.Lock, "Tersimpan di perangkat",
            "Sesi login disimpan terenkripsi (Android Keystore) dan tidak ikut backup. Daftar follower dan following hanya ada di HP-mu. Tombol Keluar menghapus sesi.",
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Nofollow bukan aplikasi resmi Instagram dan tidak berafiliasi dengan Instagram atau Meta.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
}

// ------------------------------------------------------------------------------------------ komponen

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PageBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Lingkaran gradasi besar berisi satu ikon, dengan cincin tipis di dalamnya. */
@Composable
private fun HeroBadge(icon: ImageVector, size: Int = 168) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((size * 0.78f).dp).clip(CircleShape).background(scheme.onPrimary.copy(alpha = 0.14f)))
        Icon(icon, contentDescription = null, modifier = Modifier.size((size * 0.46f).dp), tint = scheme.onPrimary)
    }
}

/** Chip kecil yang melayang pelan naik-turun; [delayMs] menggeser fasenya supaya tiap chip tidak bergerak serempak. */
@Composable
private fun FloatingChip(label: String, icon: ImageVector, modifier: Modifier, delayMs: Int) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "chip")
    val offset by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "chipOffset",
    )
    Surface(
        modifier = modifier.graphicsLayer { translationY = offset * density },
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private class Feature(val icon: ImageVector, val title: String, val subtitle: String)

@Composable
private fun FeatureCard(feature: Feature, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(feature.icon)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(feature.subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            IconTile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Kotak ikon bersudut, sama dengan gaya ikon di Pengaturan. */
@Composable
private fun IconTile(icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(40.dp).background(scheme.primaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = scheme.onPrimaryContainer)
    }
}

/** Titik halaman: yang aktif memanjang dan berwarna primary. */
@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, label = "dotColor")
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}