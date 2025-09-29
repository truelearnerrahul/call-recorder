package org.fossify.phone.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Share a file using Android Sharesheet
 */
fun Context.shareUri(file: File, mimeType: String) {
    val uri: Uri = FileProvider.getUriForFile(
        this,
        "${this.packageName}.provider", // 👈 must match authority in AndroidManifest
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    startActivity(Intent.createChooser(intent, "Share via"))
}
