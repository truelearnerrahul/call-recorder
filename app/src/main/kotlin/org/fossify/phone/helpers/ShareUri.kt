package org.fossify.phone.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.phone.network.RetrofitClient
import java.io.File

/**
 * Share a file using Android Sharesheet
 */
fun Context.shareUri(file: File, mimeType: String, uploadToServer: Boolean = false) {
    val uri: Uri = FileProvider.getUriForFile(
        this,
        "${this.packageName}.provider", // 👈 must match authority in AndroidManifest
        file
    )

    // If uploadToServer is true, upload the file to the server
    if (uploadToServer) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = RetrofitClient.uploadAudioFile(this@shareUri, uri)
                result.onSuccess { response ->
                    Log.d("ShareUri", "File uploaded successfully: ${response.url}")
                    // You can show a toast or notification here
                }.onFailure { exception ->
                    Log.e("ShareUri", "Failed to upload file", exception)
                    // Handle upload failure
                }
            } catch (e: Exception) {
                Log.e("ShareUri", "Error during file upload", e)
            }
        }
    }

    // Show the share dialog
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    startActivity(Intent.createChooser(intent, "Share via"))
}
