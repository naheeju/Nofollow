package com.nofollow.app.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** Tingkat pesan: menentukan ikon dan warna dialog. */
enum class MessageKind { SUCCESS, INFO, WARNING, ERROR }

/** Pesan untuk user, ditampilkan lewat [AppMessageDialog]. [title] singkat, [text] penjelasan. */
data class AppMessage(
    val kind: MessageKind,
    val title: String,
    val text: String,
    /** Waktu (epoch ms) kapan aksi boleh diulang; kalau terisi dialog menampilkan hitung mundur hidup. */
    val countdownUntil: Long? = null,
)

/**
 * Dialog pesan pengganti snackbar. Sengaja tidak bisa ditutup dengan ketuk di luar atau tombol back:
 * snackbar sebelumnya tertutup bottom nav dan terlewat, jadi dialog hanya hilang lewat tombol.
 * Dialog tampil di window sendiri sehingga selalu berada di atas bottom nav.
 *
 * Satu tombol ([actionLabel] -> [onDismiss]) secara default. Untuk konfirmasi (mis. peringatan sebelum memilih opsi
 * berisiko) isi [secondaryActionLabel] dan [onSecondaryAction]: muncul tombol kedua di sebelah kiri, dan [onDismiss]
 * menjadi aksi "lanjutkan".
 */
@Composable
fun AppMessageDialog(
    message: AppMessage,
    onDismiss: () -> Unit,
    actionLabel: String = "Mengerti",
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val style = message.kind.style(scheme)

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 400.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(64.dp).background(style.container, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(style.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = style.content)
                }
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                message.countdownUntil?.let {
                    Spacer(Modifier.height(16.dp))
                    CountdownChip(until = it)
                }
                Spacer(Modifier.height(24.dp))
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                            Text(secondaryActionLabel)
                        }
                        Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text(actionLabel)
                        }
                    }
                } else {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/** Pil hitung mundur yang berdetak tiap detik sampai [until]; setelah nol berganti jadi tanda sudah boleh. */
@Composable
private fun CountdownChip(until: Long) {
    val scheme = MaterialTheme.colorScheme
    val remaining by produceState(initialValue = (until - System.currentTimeMillis()).coerceAtLeast(0), until) {
        while (true) {
            value = (until - System.currentTimeMillis()).coerceAtLeast(0)
            if (value == 0L) break
            delay(1_000L.milliseconds)
        }
    }
    val done = remaining == 0L
    Row(
        modifier = Modifier
            .background(if (done) scheme.primaryContainer else scheme.surfaceContainerHighest, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tint = if (done) scheme.onPrimaryContainer else scheme.onSurfaceVariant
        Icon(
            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Timer,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Text(
            text = if (done) "Sudah bisa sinkron lagi" else "Coba lagi dalam ${formatCountdown(remaining)}",
            // Angka selebar sama supaya teks tidak bergoyang tiap detik.
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = tint,
        )
    }
}

private fun formatCountdown(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

private class MessageStyle(val icon: ImageVector, val container: Color, val content: Color)

private fun MessageKind.style(scheme: ColorScheme): MessageStyle = when (this) {
    MessageKind.SUCCESS -> MessageStyle(Icons.Outlined.CheckCircle, scheme.primaryContainer, scheme.onPrimaryContainer)
    MessageKind.INFO -> MessageStyle(Icons.Outlined.Info, scheme.secondaryContainer, scheme.onSecondaryContainer)
    MessageKind.WARNING -> MessageStyle(Icons.Outlined.WarningAmber, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    MessageKind.ERROR -> MessageStyle(Icons.Outlined.ErrorOutline, scheme.errorContainer, scheme.onErrorContainer)
}