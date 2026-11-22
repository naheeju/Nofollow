package com.nofollow.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Foto dari file lokal; selama belum ada, lingkaran dengan [fallbackLetter] (biasanya huruf pertama username). */
@Composable
fun ProfileAvatar(
    avatarPath: String?,
    avatarVersion: Long?,
    fallbackLetter: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val bitmap by produceState<ImageBitmap?>(null, avatarPath, avatarVersion) {
        value = if (avatarPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(avatarPath)?.asImageBitmap() }
        }
    }
    val shape = modifier.size(size).clip(CircleShape)
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = shape)
    } else {
        Box(
            modifier = shape.background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = fallbackLetter ?: "?", style = MaterialTheme.typography.titleMedium)
        }
    }
}