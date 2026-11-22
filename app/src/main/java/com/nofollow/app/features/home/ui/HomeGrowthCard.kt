package com.nofollow.app.features.home.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nofollow.app.features.home.domain.model.GrowthPoint
import com.nofollow.app.features.home.domain.model.HomeGrowth
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

/** Selisih terkecil yang dipakai untuk skala sumbu Y, supaya naik 1-2 follower tidak tampak seperti lonjakan. */
private const val MIN_SPAN = 6f

/**
 * Kartu "Pertumbuhan Akun": grafik garis halus dari riwayat follower per hari + badge perubahan.
 * Grafik butuh data minimal dua hari; sebelum itu tampil keadaan kosong yang menjelaskan kenapa.
 */
@Composable
internal fun GrowthCard(
    growth: HomeGrowth?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Pertumbuhan Akun",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = growth?.let { "${NumberFormat.getInstance().format(it.current)} follower" }
                            ?: "Jumlah follower 30 hari terakhir",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (growth != null && growth.hasChart) DeltaBadge(growth)
            }
            Spacer(Modifier.height(20.dp))
            if (growth != null && growth.hasChart) {
                GrowthChart(points = growth.points, modifier = Modifier.fillMaxWidth().height(96.dp))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(startLabel(growth.spanDays), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text("Hari ini", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            } else {
                GrowthEmpty(hasAnyData = growth != null)
            }
        }
    }
}

private fun startLabel(spanDays: Int) = when {
    spanDays >= 30 -> "30 hari lalu"
    spanDays == 1 -> "Kemarin"
    else -> "$spanDays hari lalu"
}

@Composable
private fun DeltaBadge(growth: HomeGrowth) {
    val scheme = MaterialTheme.colorScheme
    val up = growth.delta > 0
    val down = growth.delta < 0
    val icon: ImageVector = when {
        up -> Icons.AutoMirrored.Outlined.TrendingUp
        down -> Icons.AutoMirrored.Outlined.TrendingDown
        else -> Icons.AutoMirrored.Outlined.TrendingFlat
    }
    val container: Color = when {
        up -> scheme.tertiaryContainer
        down -> scheme.errorContainer
        else -> scheme.surfaceContainerHighest
    }
    val content: Color = when {
        up -> scheme.onTertiaryContainer
        down -> scheme.onErrorContainer
        else -> scheme.onSurfaceVariant
    }
    val label = growth.deltaPercent?.let { String.format(Locale.getDefault(), "%+.1f%%", it) }
        ?: String.format(LocalLocale.current.platformLocale, "%+d", growth.delta)
    Row(
        modifier = Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
    }
}

/** Belum ada dua hari data: ikon + penjelasan singkat, tinggi sama dengan grafik supaya kartu tidak melompat. */
@Composable
private fun GrowthEmpty(hasAnyData: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(scheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Timeline, contentDescription = null, modifier = Modifier.size(26.dp), tint = scheme.onSecondaryContainer)
        }
        Text(
            text = if (hasAnyData) "Grafik segera muncul" else "Belum ada data",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = if (hasAnyData) {
                "Dibutuhkan data dari minimal dua hari. Jumlah follower dicatat tiap sinkron selesai."
            } else {
                "Jumlah follower akan dicatat tiap sinkron selesai, lalu tampil di sini sebagai grafik."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Posisi X mengikuti hari sebenarnya (hari tanpa data membuat jarak lebih lebar), bukan urutan titik. */
@Composable
private fun GrowthChart(points: List<GrowthPoint>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.primary
    val fillTop = scheme.primary.copy(alpha = 0.22f)
    val fillBottom = scheme.primary.copy(alpha = 0f)
    val dotRing = scheme.surfaceContainerLow

    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val firstDay = points.first().day
        val dayRange = (points.last().day - firstDay).coerceAtLeast(1).toFloat()
        val low = points.minOf { it.followers }.toFloat()
        val high = points.maxOf { it.followers }.toFloat()
        val span = maxOf(high - low, MIN_SPAN)
        // Data dipusatkan di rentang skala, jadi garis datar berada di tengah, bukan menempel di dasar.
        val base = low - (span - (high - low)) / 2f
        val pad = size.height * 0.12f

        fun x(i: Int) = (points[i].day - firstDay) / dayRange * size.width
        fun y(i: Int) = size.height - pad - (points[i].followers - base) / span * (size.height - pad * 2)

        val curve = Path().apply {
            moveTo(x(0), y(0))
            for (i in 1 until points.size) {
                val mid = (x(i - 1) + x(i)) / 2f
                cubicTo(mid, y(i - 1), mid, y(i), x(i), y(i))
            }
        }
        val area = Path().apply {
            addPath(curve)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(fillTop, fillBottom)))
        drawPath(curve, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        val end = Offset(x(points.lastIndex), y(points.lastIndex))
        drawCircle(dotRing, radius = 7.dp.toPx(), center = end)
        drawCircle(line, radius = 5.dp.toPx(), center = end)
    }
}