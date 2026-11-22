package com.nofollow.app.ui.splash

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nofollow.app.R
import kotlinx.coroutines.delay

/** Ukuran ikon di splash sistem tanpa latar ikon (Android 12+ dan library splash): 288dp dari kanvas 108dp. */
private val IconSize = 288.dp

/** Jarak teks dari tepi bawah layar (di atas bilah navigasi), supaya agak naik dan jauh dari ikon di tengah. */
private val TextBottomPadding = 56.dp

private const val FADE_OUT_MS = 300

/**
 * Lapisan merek di atas app saat dibuka: ikon di posisi dan ukuran yang sama dengan splash sistem, lalu teks
 * "Nofollow" dan "by: ..." di dekat bawah layar. Splash sistem memudar ke lapisan ini (ikonnya tampak diam), jadi
 * yang terlihat hanya teks yang muncul di bagian bawah.
 *
 * Lapisan memudar kalau [ready] (status login sudah terbaca) dan sudah [totalMs] sejak [startedAtMs]
 * (SystemClock.elapsedRealtime saat app dibuka), jadi total splash tidak lebih lama dari perlu.
 *
 * Warna ikut mode terang/gelap lewat @color/splash_* (values dan values-night), sama dengan splash sistem.
 * Hanya tampil sekali per proses: setelah hilang, rotasi atau ganti tema tidak memunculkannya lagi.
 */
@Composable
fun SplashBrandOverlay(ready: Boolean, startedAtMs: Long, totalMs: Long) {
    var visible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(ready) {
        if (ready && visible) {
            delay((totalMs - (SystemClock.elapsedRealtime() - startedAtMs)).coerceAtLeast(0L))
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(FADE_OUT_MS))) {
        SplashBrand()
    }
}

@Composable
private fun SplashBrand() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.splash_background))
            // Menelan ketukan supaya app di bawahnya tidak bisa disentuh selama splash.
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = TextBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.splash_title),
            )
            Text(
                text = stringResource(R.string.splash_byline),
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.splash_subtitle),
            )
        }
    }
}