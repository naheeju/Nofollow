package com.nofollow.app.features.setting.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofollow.app.core.common.formatStamp
import com.nofollow.app.features.tracker.domain.model.SyncMode
import com.nofollow.app.features.tracker.domain.model.TrackerStatus
import com.nofollow.app.ui.components.AppMessage
import com.nofollow.app.ui.components.AppMessageDialog
import com.nofollow.app.ui.components.GroupPosition
import com.nofollow.app.ui.components.GroupedCard
import com.nofollow.app.ui.components.GroupedCardGap
import com.nofollow.app.ui.components.LocalBottomBarInset
import com.nofollow.app.ui.components.MessageKind
import com.nofollow.app.ui.components.ScreenTopBar
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import androidx.core.net.toUri

/**
 * Saklar tombol tester endpoint di bagian "Lainnya". Matikan untuk rilis; kodenya (ViewModel, use case, tester)
 * sengaja tidak dihapus supaya bisa dipakai lagi untuk menguji endpoint lain.
 */
private const val SHOW_DEBUG_TOOLS = false

/**
 * Layar Pengaturan. Bagian "Pelacakan & sinkron" dan "Keamanan & batas" sudah terhubung ke fungsi yang ada
 * (mode sinkron, pantau latar belakang, status perlindungan). Bagian lain menyusul.
 *
 * Gaya daftar: tiap kategori berisi kartu-kartu terpisah dengan jarak tipis (bukan garis pemisah). Sudut luar
 * kategori besar, sudut antar kartu kecil; saat ditekan, kartu membulat penuh dan terlepas dari grupnya.
 *
 * BatteryLife disuppress: app ini tidak didistribusikan lewat Play Store, jadi kebijakan
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dari Play tidak berlaku. Sinkron otomatis memang butuh jadwal tepat waktu.
 */
@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    state.result?.let { AppMessageDialog(message = it, onDismiss = viewModel::dismissResult) }

    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var batteryExempt by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val haptic = LocalHapticFeedback.current

    // vibrate = false kalau dipanggil dari ketukan baris: GroupedCard sudah bergetar sendiri (hindari getar dobel).
    fun setProbe(on: Boolean, vibrate: Boolean = true) {
        // Getaran yang sama dengan ketukan tab di Tracker.
        if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        viewModel.setProbe(on)
        val needsPermission = on && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Cek pembaruan: izin notifikasi diminta saat dinyalakan supaya kabar versi baru bisa sampai walau app tertutup.
    fun setUpdateCheck(on: Boolean, vibrate: Boolean = true) {
        if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        viewModel.setUpdateCheck(on)
        val needsPermission = on && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requestBatteryExemption() {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri(),
            ),
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { ScreenTopBar(title = "Pengaturan", scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------------------ pelacakan & sinkron
            item { SectionLabel("Pelacakan & sinkron") }
            item {
                SettingGroup {
                    entry { position ->
                        SettingRow(
                            position = position,
                            icon = Icons.Outlined.Sync,
                            title = "Sinkron otomatis",
                            subtitle = "Sekitar tiap ${intervalText(state.mode)} di luar jam tenang. Atur di Keamanan & batas. Jam tenang dimulai 00:00 - 07:00",
                            trailing = {
                                Text(
                                    text = state.mode.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.primary,
                                )
                            },
                        )
                    }
                    entry { position ->
                        SettingRow(
                            position = position,
                            icon = Icons.Filled.Notifications,
                            title = "Notifikasi latar belakang",
                            subtitle = "Sinkron berjalan di latar belakang sesuai mode. Ada notifikasi \"Menyinkron data\" selama berjalan.",
                            onClick = { setProbe(!state.probeEnabled, vibrate = false) },
                            trailing = {
                                Switch(checked = state.probeEnabled, onCheckedChange = { setProbe(it) })
                            },
                        )
                    }
                    if (state.probeEnabled) {
                        entry { position ->
                            if (batteryExempt) {
                                SettingRow(
                                    position = position,
                                    icon = Icons.Outlined.BatteryFull,
                                    title = "Baterai tanpa batas",
                                    subtitle = "Sudah diizinkan jalan di background, jadi sinkron otomatis tetap jalan walau app dibuang dari recents.",
                                    trailing = { StatusPill("Diizinkan", Tone.OK) },
                                )
                            } else {
                                SettingRow(
                                    position = position,
                                    icon = Icons.Outlined.BatterySaver,
                                    title = "Aktifkan tanpa batas",
                                    subtitle = "Agar sinkron otomatis jalan dengan baik, ubah pengaturan baterai ke Tidak ada pembatasan.",
                                    onClick = ::requestBatteryExemption,
                                    trailing = { TextButton(onClick = ::requestBatteryExemption) { Text("Izinkan") } },
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------------------ keamanan & batas
            item { SectionLabel("Keamanan & batas") }
            item {
                val status = state.status
                val protection = protectionOf(status, System.currentTimeMillis())
                SettingGroup {
                    entry { position ->
                        GroupedCard(position = position) {
                            SettingRowContent(
                                icon = Icons.Filled.Shield,
                                title = "Perlindungan akun Instagram",
                                subtitle = protection.subtitle,
                                trailing = { StatusPill(protection.label, protection.tone) },
                            )
                            if (status?.haltReason != null) {
                                Row(Modifier.fillMaxWidth().padding(start = 76.dp, end = 16.dp, bottom = 12.dp)) {
                                    FilledTonalButton(onClick = viewModel::clearHalt) { Text("Sudah beres, lanjutkan") }
                                }
                            }
                        }
                    }
                    entry { position ->
                        ModeSection(
                            position = position,
                            current = state.mode,
                            estimates = state.estimates,
                            status = status,
                            onSelect = viewModel::selectMode,
                        )
                    }
                }
            }

            // ------------------------------------------------------------ pembaruan
            item { SectionLabel("Pembaruan") }
            item {
                SettingGroup {
                    entry { position ->
                        SettingRow(
                            position = position,
                            icon = Icons.Outlined.SystemUpdate,
                            title = "Cek pembaruan",
                            subtitle = "Cek versi baru Nofollow, dikabarin lewat notifikasi dan info di Beranda. " +
                                    "Versi terpasang ${state.installedVersion}.",
                            onClick = { setUpdateCheck(!state.updateEnabled, vibrate = false) },
                            trailing = {
                                Switch(checked = state.updateEnabled, onCheckedChange = { setUpdateCheck(it) })
                            },
                        )
                    }
                }
            }

            // ------------------------------------------------------------ lainnya
            item { SectionLabel("Lainnya") }
            item {
                SettingGroup {
                    // Tombol tester disembunyikan; kode tetap ada. Ubah SHOW_DEBUG_TOOLS jadi true untuk memunculkan lagi.
                    if (SHOW_DEBUG_TOOLS) {
                        entry { position ->
                            SettingRow(
                                position = position,
                                icon = Icons.Filled.BugReport,
                                title = "Tes endpoint jumlah follower",
                                subtitle = if (state.testing) "Sedang tes..." else "1 request. Hasil di dialog dan logcat (NofollowCountTest)",
                                enabled = !state.testing,
                                onClick = viewModel::runCountTest,
                            )
                        }
                        entry { position ->
                            SettingRow(
                                position = position,
                                icon = Icons.Filled.BugReport,
                                title = "Tes endpoint penonton story",
                                subtitle = if (state.testing) "Sedang tes..." else "2 request. Hasil di dialog dan logcat (NofollowStoryTest)",
                                enabled = !state.testing,
                                onClick = viewModel::runStoryTest,
                            )
                        }
                    }
                    entry { position ->
                        SettingRow(
                            position = position,
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            title = "Keluar",
                            subtitle = "Hapus sesi login dari app ini",
                            onClick = onLogout,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- mode

/** Slider kecepatan cek: Hemat, Normal (rekomendasi), Cepat, Kilat. Bisa digeser atau diketuk labelnya. */
@Composable
private fun ModeSection(
    position: GroupPosition,
    current: SyncMode,
    estimates: Map<SyncMode, ModeEstimate>,
    status: TrackerStatus?,
    onSelect: (SyncMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val modes = SyncMode.entries
    // Pilihan lokal supaya slider tidak melompat balik sebelum penyimpanan selesai; direset kalau nilai tersimpan berubah.
    var picked by remember(current) { mutableStateOf(current) }
    var warnKilat by remember { mutableStateOf(false) }

    // Kilat boleh dipilih, tapi lewat dialog peringatan dulu. Mode lain langsung tersimpan.
    fun commit(mode: SyncMode) {
        if (mode == SyncMode.KILAT) warnKilat = true else onSelect(mode)
    }

    if (warnKilat) {
        AppMessageDialog(
            message = AppMessage(
                kind = MessageKind.WARNING,
                title = "Mode Kilat tidak disarankan",
                text = "Kilat menyapu sekitar tiap 2 jam, jadi request ke Instagram jauh lebih banyak dan plafon harian " +
                        "dijepit, sehingga 2 jam pun tidak selalu tercapai. Risiko kena pembatasan atau checkpoint " +
                        "akun lebih besar, apalagi di akun dengan follower dan following banyak. Disarankan pakai " +
                        "Normal atau Cepat.",
            ),
            onDismiss = {
                warnKilat = false
                onSelect(SyncMode.KILAT)
            },
            actionLabel = "Tetap pakai",
            secondaryActionLabel = "Batal",
            onSecondaryAction = {
                warnKilat = false
                picked = current
            },
        )
    }

    fun choose(mode: SyncMode) {
        if (mode == picked) return
        picked = mode
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val estimate = estimates[picked]
    GroupedCard(position = position) {
        SettingRowContent(
            icon = Icons.Filled.Speed,
            title = "Kecepatan cek",
            subtitle = estimate?.let { "Sekitar ${it.sweepsPerDay} sapuan/hari, plafon ${it.requestCap} request/hari" }
                ?: "Sekitar tiap ${intervalText(picked)}",
            trailing = {
                Text(text = picked.label, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            },
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Slider(
                value = picked.ordinal.toFloat(),
                onValueChange = { choose(modes[it.roundToInt().coerceIn(0, modes.lastIndex)]) },
                onValueChangeFinished = { if (picked != current) commit(picked) },
                valueRange = 0f..modes.lastIndex.toFloat(),
                steps = modes.size - 2,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                modes.forEach { mode ->
                    val selected = mode == picked
                    Column(
                        modifier = Modifier
                            .clickable {
                                choose(mode)
                                if (mode != current) commit(mode)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (mode == SyncMode.NORMAL) "Rekomendasi" else " ",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.tertiary,
                        )
                    }
                }
            }
            Text(
                text = modeHint(picked),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            status?.let { s ->
                val next = s.nextSweepAt?.let { ", sapuan berikutnya sekitar ${formatStamp(it)}" }.orEmpty()
                Text(
                    text = "Request hari ini: ${s.requestsToday}$next",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun intervalText(mode: SyncMode): String = when (mode) {
    SyncMode.HEMAT -> "12 jam"
    SyncMode.NORMAL -> "6 jam"
    SyncMode.CEPAT -> "3 jam"
    SyncMode.KILAT -> "2 jam"
}

private fun modeHint(mode: SyncMode): String = when (mode) {
    SyncMode.HEMAT -> "Paling hemat request. Unfollow terdeteksi paling lambat."
    SyncMode.NORMAL -> "Seimbang antara kecepatan dan keamanan akun. Disarankan."
    SyncMode.CEPAT -> "Lebih cepat tahu, request lebih banyak."
    SyncMode.KILAT -> "Paling berisiko dan plafonnya dijepit, jadi 2 jam tidak selalu tercapai. Tidak disarankan."
}

// ---------------------------------------------------------------------------- perlindungan

private enum class Tone { OK, WARN, STOP, NEUTRAL }

private class Protection(val label: String, val subtitle: String, val tone: Tone)

private fun protectionOf(status: TrackerStatus?, now: Long): Protection = when {
    status == null -> Protection("Memuat", "Membaca status...", Tone.NEUTRAL)
    status.haltReason != null ->
        Protection("Berhenti", "Berhenti: ${status.haltReason}. Selesaikan dulu di Instagram atau login ulang.", Tone.STOP)
    now < status.cooldownUntil -> {
        // penaltyUntil hanya diisi untuk throttle sungguhan (429 / action blocked); gagal koneksi hanya cooldown singkat.
        val throttled = now < status.penaltyUntil
        val why = if (throttled) "Instagram membatasi request" else "koneksi ke Instagram gagal, dilanjutkan otomatis"
        Protection("Cooldown", "Istirahat sampai ${formatStamp(status.cooldownUntil)} karena $why.", Tone.WARN)
    }
    now < status.penaltyUntil ->
        Protection("Dilambatkan", "Jeda sapuan dilipat dua sampai ${formatStamp(status.penaltyUntil)}.", Tone.WARN)
    else -> Protection("Aktif", "Jeda acak, plafon request harian, dan cooldown otomatis berjalan.", Tone.OK)
}

@Composable
private fun StatusPill(label: String, tone: Tone) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        Tone.OK -> scheme.primaryContainer to scheme.onPrimaryContainer
        Tone.WARN -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Tone.STOP -> scheme.errorContainer to scheme.onErrorContainer
        Tone.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Text(
        text = label,
        modifier = Modifier.background(container, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = content,
    )
}

// -------------------------------------------------------------------------- komponen umum

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private class SettingGroupScope {
    val entries = mutableListOf<@Composable (GroupPosition) -> Unit>()
    fun entry(content: @Composable (GroupPosition) -> Unit) {
        entries += content
    }
}

/** Satu kategori: kartu-kartu terpisah dengan jarak tipis. Posisi tiap kartu dihitung dari urutan `entry`. */
@Composable
private fun SettingGroup(build: SettingGroupScope.() -> Unit) {
    val entries = SettingGroupScope().apply(build).entries
    Column(verticalArrangement = Arrangement.spacedBy(GroupedCardGap)) {
        entries.forEachIndexed { index, content ->
            content(GroupPosition.of(index, entries.size))
        }
    }
}

@Composable
private fun SettingRow(
    position: GroupPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    GroupedCard(position = position, modifier = modifier, onClick = onClick, enabled = enabled) {
        SettingRowContent(icon = icon, title = title, subtitle = subtitle, trailing = trailing)
    }
}

/** Isi baris: ikon kotak bersudut (warna primary), judul, keterangan, dan elemen kanan. */
@Composable
private fun SettingRowContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(scheme.primaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = scheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}