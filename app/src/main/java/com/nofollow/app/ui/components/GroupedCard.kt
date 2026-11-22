package com.nofollow.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Posisi kartu di dalam satu kelompok; menentukan sudut mana yang besar (tepi luar kelompok) dan mana yang kecil (antar kartu). */
enum class GroupPosition {
    SINGLE, FIRST, MIDDLE, LAST;

    companion object {
        fun of(index: Int, count: Int): GroupPosition = when {
            count <= 1 -> SINGLE
            index == 0 -> FIRST
            index == count - 1 -> LAST
            else -> MIDDLE
        }
    }
}

/** Jarak tipis antar kartu dalam satu kelompok (bukan garis pemisah). */
val GroupedCardGap: Dp = 3.dp

private val OuterCorner = 28.dp
private val InnerCorner = 6.dp

/**
 * Kartu satu item gaya Pengaturan. Sudut luar kelompok besar, sudut antar kartu kecil. Saat ditekan, semua sudut
 * membulat penuh dan kartu sedikit mengecil, jadi terasa terlepas dari kelompoknya lalu kembali saat dilepas.
 * Ketukan ([onClick]) bergetar (SegmentTick, sama dengan tab Tracker). Tanpa [onClick] dan [onLongClick] kartu tidak bisa ditekan. Beri jarak [GroupedCardGap] antar kartu di pemanggil.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedCard(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val clickable = onClick != null || onLongClick != null
    val active = pressed && clickable && enabled

    val topCorner = if (position == GroupPosition.FIRST || position == GroupPosition.SINGLE) OuterCorner else InnerCorner
    val bottomCorner = if (position == GroupPosition.LAST || position == GroupPosition.SINGLE) OuterCorner else InnerCorner
    val cornerSpec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val top by animateDpAsState(if (active) OuterCorner else topCorner, cornerSpec, label = "topCorner")
    val bottom by animateDpAsState(if (active) OuterCorner else bottomCorner, cornerSpec, label = "bottomCorner")
    val scale by animateFloatAsState(
        targetValue = if (active) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (clickable) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        onLongClick = onLongClick,
                        onClick = {
                            // Getaran yang sama dengan ketukan tab di Tracker; tekan lama sudah bergetar sendiri.
                            if (onClick != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onClick()
                            }
                        },
                    )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}