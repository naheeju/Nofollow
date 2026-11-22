package com.nofollow.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nofollow.app.navigation.AppNavHost
import com.nofollow.app.navigation.AppViewModel
import com.nofollow.app.navigation.AuthGate
import com.nofollow.app.ui.splash.SplashBrandOverlay
import com.nofollow.app.ui.theme.NofollowTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Harus dipanggil sebelum super.onCreate: mengganti tema peluncur (Theme.Nofollow.Starting) ke tema biasa.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Splash tampil tiap app dibuka, dan bertahan sampai status login terbaca (tidak ada kilasan layar kosong),
        // dengan tampil minimal SPLASH_MIN_MS supaya tidak berkedip kalau status terbaca sangat cepat.
        val shownAt = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition {
            appViewModel.gate.value is AuthGate.Loading || SystemClock.elapsedRealtime() - shownAt < SPLASH_MIN_MS
        }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge()
        setContent {
            NofollowTheme {
                val gate by appViewModel.gate.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    AppNavHost(viewModel = appViewModel)
                    // Teks merek di bawah ikon; total splash (sistem + merek) sekitar SPLASH_TOTAL_MS sejak app dibuka.
                    SplashBrandOverlay(
                        ready = gate !is AuthGate.Loading,
                        startedAtMs = shownAt,
                        totalMs = SPLASH_TOTAL_MS,
                    )
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MIN_MS = 600L
        const val SPLASH_TOTAL_MS = 1400L
        const val SPLASH_FADE_MS = 250L
    }
}