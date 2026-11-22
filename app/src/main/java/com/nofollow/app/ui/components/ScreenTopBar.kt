package com.nofollow.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

/**
 * Top bar medium yang dipakai semua layar utama: judul besar saat di atas, mengecil mulus ke bar kecil saat
 * daftar digulir. Ukuran huruf diinterpolasi dari [TopAppBarScrollBehavior] sehingga ikut gerakan scroll
 * frame demi frame. Pakai bersama `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
) {
    val background = MaterialTheme.colorScheme.background

    MediumTopAppBar(
        modifier = modifier,
        expandedHeight = 128.dp,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = background,
            scrolledContainerColor = background,
            navigationIconContentColor = Color.Unspecified,
            titleContentColor = Color.Unspecified,
            actionIconContentColor = Color.Unspecified
        ),
        title = {
            val fraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
            Text(
                text = title,
                fontSize = lerp(36.sp, 22.sp, fraction),
                lineHeight = lerp(44.sp, 28.sp, fraction),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}