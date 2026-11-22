package com.nofollow.app.features.update.ui

import androidx.compose.runtime.Composable
import com.nofollow.app.features.update.domain.model.AppUpdate
import com.nofollow.app.ui.components.AppMessage
import com.nofollow.app.ui.components.AppMessageDialog
import com.nofollow.app.ui.components.MessageKind

/** Dialog "versi baru tersedia". [onDownload] = tombol Unduh, [onLater] = Nanti. */
@Composable
fun UpdatePromptDialog(
    update: AppUpdate,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    AppMessageDialog(
        message = AppMessage(
            kind = MessageKind.INFO,
            title = "Versi baru tersedia",
            text = "Nofollow ${update.version} sudah dirilis. Perbarui supaya app tetap berjalan lancar.",
        ),
        // Di AppMessageDialog, onDismiss adalah aksi tombol utama; tombol kedua ada di sebelah kirinya.
        onDismiss = onDownload,
        actionLabel = "Unduh",
        secondaryActionLabel = "Nanti",
        onSecondaryAction = onLater,
    )
}