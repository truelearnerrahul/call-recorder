package org.fossify.phone.activities

import android.content.ClipData
import android.content.Intent
import android.database.Cursor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import org.fossify.phone.helpers.AuthHelper
import org.fossify.phone.network.RetrofitClient
import org.fossify.phone.network.UploadResponse

data class RecordingItem(
    var file: File,
    val duration: Long,
    val isShared: Boolean,
    var isUploaded: Boolean = false,
    var isUploading: Boolean = false
)

class RecordingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.recording_title)
    val subtitle: TextView = view.findViewById(R.id.recording_subtitle)
    val timer: TextView = view.findViewById(R.id.recording_timer)
    val seekbar: SeekBar = view.findViewById(R.id.recording_seekbar)
    val playPause: ImageButton = view.findViewById(R.id.play_pause)
    val upload: ImageButton = view.findViewById(R.id.upload)
    val more: ImageButton = view.findViewById(R.id.more)
    val badge: TextView = view.findViewById(R.id.recording_badge)
}

private interface RecordingClickListener {
    fun onPlayPauseClicked(item: RecordingItem, holder: RecordingViewHolder)
    fun onRenameRequested(item: RecordingItem)
    fun onDeleteRequested(item: RecordingItem)
    fun onUploadRequested(item: RecordingItem)
}

private class RecordingsAdapter(
    var items: List<RecordingItem>,
    private val activeUploads: Map<String, Boolean>,
    private val listener: RecordingClickListener
) : RecyclerView.Adapter<RecordingViewHolder>() {
    
    fun updateItems(newItems: List<RecordingItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.file.nameWithoutExtension

        // Format date/time and duration locally to avoid extension dependencies
        val ctx = holder.itemView.context
        val date = java.util.Date(item.file.lastModified())
        val datePart = android.text.format.DateFormat.getMediumDateFormat(ctx).format(date)
        val timePart = android.text.format.DateFormat.getTimeFormat(ctx).format(date)
        val totalSeconds = (item.duration / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val durationText = String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)

        holder.subtitle.text = "$datePart $timePart • $durationText"
        holder.timer.text = "0:00"
        holder.seekbar.max = item.duration.toInt()
        holder.seekbar.progress = 0

        // Show/hide shared badge
        holder.badge.visibility = if (item.isShared) View.VISIBLE else View.GONE
        if (item.isShared) holder.badge.text = ctx.getString(R.string.shared)

        // Handle upload button state
        when {
            item.isUploading -> {
                holder.upload.visibility = View.VISIBLE
                holder.upload.setImageResource(R.drawable.ic_loading)
                holder.upload.isEnabled = false
            }
            item.isUploaded -> {
                holder.upload.visibility = View.GONE
            }
            else -> {
                holder.upload.visibility = View.VISIBLE
                holder.upload.setImageResource(R.drawable.ic_cloud_upload_24)
                holder.upload.isEnabled = true
            }
        }

        holder.playPause.setOnClickListener { listener.onPlayPauseClicked(item, holder) }
        holder.upload.setOnClickListener { listener.onUploadRequested(item) }

        // 3-dots overflow menu with icons (Rename/Delete)
        holder.more.setOnClickListener { v ->
            val popup = PopupMenu(v.context, holder.more)
            popup.menu.add(0, 1, 0, v.context.getString(R.string.rename)).setIcon(R.drawable.ic_edit_24)
            popup.menu.add(0, 2, 1, v.context.getString(R.string.delete)).setIcon(R.drawable.ic_delete_24)

            // Force show icons on PopupMenu where supported
            try {
                val fields = popup.javaClass.getDeclaredField("mPopup")
                fields.isAccessible = true
                val menuHelper = fields.get(popup)
                val clazz = menuHelper.javaClass
                val setForceIcons = clazz.getDeclaredMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                setForceIcons.invoke(menuHelper, true)
            } catch (_: Exception) { }

            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> listener.onRenameRequested(item)
                    2 -> listener.onDeleteRequested(item)
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return items[position].file.absolutePath.hashCode().toLong()
    }
}

private const val UPLOADED_RECORDINGS_PREFS = "uploaded_recordings_prefs"
private const val KEY_UPLOADED_PATHS = "uploaded_paths"
object UploadedRecordingsManager {
    private fun getPrefs(context: android.content.Context) =
        context.getSharedPreferences(UPLOADED_RECORDINGS_PREFS, android.content.Context.MODE_PRIVATE)

    fun getUploadedPaths(context: android.content.Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_UPLOADED_PATHS, emptySet()) ?: emptySet()
    }

    fun addUploadedPath(context: android.content.Context, path: String) {
        val prefs = getPrefs(context)
        val currentPaths = getUploadedPaths(context).toMutableSet()
        currentPaths.add(path)
        prefs.edit().putStringSet(KEY_UPLOADED_PATHS, currentPaths).apply()
    }

    fun removeUploadedPath(context: android.content.Context, path: String) {
        val prefs = getPrefs(context)
        val currentPaths = getUploadedPaths(context).toMutableSet()
        if (currentPaths.remove(path)) {
            prefs.edit().putStringSet(KEY_UPLOADED_PATHS, currentPaths).apply()
        }
    }
}

class RecordingsActivity : SimpleActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var currentItem: RecordingItem? = null
    private var currentHolder: RecordingViewHolder? = null
    private val activeUploads = mutableMapOf<String, Boolean>() // key = file path, value = uploading


    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            val holder = currentHolder
            if (mp != null && holder != null && mp.isPlaying) {
                val pos = mp.currentPosition
                holder.timer.text = formatMs(pos.toLong())
                holder.seekbar.progress = pos
                mainHandler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)

        recyclerView = findViewById(R.id.recordings_list)
        emptyView = findViewById(R.id.empty_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Handle import via share intents
        val importedCount = handleShareIntentIfAny(intent)
        if (importedCount > 0) {
            toast(getString(R.string.import_completed, importedCount))
        }

        setupRecyclerView()
    }

    private fun handleShareIntentIfAny(intent: Intent?): Int {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) {
            return -1
        }

        val uris = if (intent.action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        } else {
            extractUrisFromClipData(intent.clipData)
        }

        if (uris.isEmpty()) {
            toast(getString(R.string.no_recordings_found))
            return -1
        }

        var importedCount = 0
        for (uri in uris) {
            if (importOne(uri)) {
                importedCount++
            }
        }

        // After importing, trigger upload for the last imported file
        if (importedCount > 0) {
            val items = loadRecordings()
            if (items.isNotEmpty()) {
                // Get the most recently added file (should be the one just imported)
                val lastImported = items.first()
                uploadRecording(lastImported)
            }
        }
        
        return importedCount
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val importedCount = handleShareIntentIfAny(intent)
        if (importedCount >= 0) {
            setupRecyclerView()
        }
    }

    private fun extractUrisFromClipData(clipData: ClipData?): ArrayList<Uri> {
        val list = arrayListOf<Uri>()
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { list.add(it) }
            }
        }
        return list
    }

    private fun importOne(uri: Uri): Boolean {
        return try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            if (!dir.exists()) dir.mkdirs()

            val name = queryDisplayName(uri) ?: "shared_${System.currentTimeMillis()}.wav"
            val safeName = ensureExtension(name, defaultExt = ".wav")
            val target = uniqueFile(dir, "Shared_" + safeName.removePrefix("Shared_"))

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    copyStream(input, output)
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private fun copyStream(input: InputStream, output: FileOutputStream) {
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun ensureExtension(name: String, defaultExt: String): String {
        return if (name.contains('.')) name else name + defaultExt
    }
    
    private fun queryDisplayName(uri: Uri): String? {
        return try {
            var name: String? = null
            val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                }
            }
            name
        } catch (e: Exception) {
            showErrorToast(e)
            null
        }
    }

    private fun uniqueFile(dir: File, baseName: String): File {
        var file = File(dir, baseName)
        if (!file.exists()) return file
        val dot = baseName.lastIndexOf('.')
        val (prefix, ext) = if (dot != -1) baseName.substring(0, dot) to baseName.substring(dot) else baseName to ""
        var index = 1
        while (true) {
            val candidate = File(dir, "$prefix($index)$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun loadRecordings(): List<RecordingItem> {
        val recordingsDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
            return emptyList()
        }

        // Get the set of uploaded file paths from SharedPreferences
        val uploadedPaths = UploadedRecordingsManager.getUploadedPaths(this)

        val currentItems = (recyclerView.adapter as? RecordingsAdapter)?.let { adapter ->
            adapter.items.associateBy { it.file.absolutePath }
        } ?: emptyMap()

        return recordingsDir.listFiles()
            ?.filter { it.extension in listOf("mp3", "wav", "m4a", "aac") }
            ?.map { file ->
                currentItems[file.absolutePath]?.let { existingItem ->
                    existingItem.file = file
                    // Ensure the isUploaded state is also updated from persistence
                    existingItem.isUploaded = uploadedPaths.contains(file.absolutePath)
                    existingItem
                } ?: RecordingItem(
                    file,
                    readDuration(file),
                    false,
                    // Check if the file path exists in our saved set
                    isUploaded = uploadedPaths.contains(file.absolutePath)
                )
            }
            ?.sortedByDescending { it.file.lastModified() }
            ?: emptyList()
    }
    
    private fun readDuration(file: File): Long {
        return try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            val duration = mediaPlayer.duration.toLong()
            mediaPlayer.release()
            duration
        } catch (e: Exception) {
            showErrorToast(e)
            0L
        }
    }
    
    private fun deleteRecording(item: RecordingItem) {
        try {
            val filePath = item.file.absolutePath
            if (item.file.delete()) {
                UploadedRecordingsManager.removeUploadedPath(this, filePath)
                val updatedList = loadRecordings()
                (recyclerView.adapter as? RecordingsAdapter)?.updateItems(updatedList)
                emptyView.beGoneIf(updatedList.isNotEmpty())
                toast(R.string.deleted)
            } else {
                toast(R.string.delete_failed)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun showErrorToast(e: Exception) {
        val errorMessage = e.message ?: "An unknown error occurred"
        toast("Error: $errorMessage")
    }
    
    // The actual implementation should be in the onNewIntent method
    private fun refreshList() {
        val items = loadRecordings()
        (recyclerView.adapter as? RecordingsAdapter)?.let { adapter ->
            adapter.updateItems(items)
        } ?: run {
            setupRecyclerView()
        }
        emptyView.beGoneIf(items.isNotEmpty())
    }
    
    private fun handlePlayPause(item: RecordingItem, holder: RecordingViewHolder) {
        try {
            if (mediaPlayer == null || currentItem != item) {
                // New playback
                currentItem?.let { stopPlayback() }
                currentItem = item
                currentHolder = holder
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(item.file.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.start()
                        holder.playPause.setImageResource(android.R.drawable.ic_media_pause)
                        mainHandler.post(tickRunnable)
                    }
                    setOnCompletionListener {
                        stopPlayback()
                    }
                    prepareAsync()
                }
            } else {
                // Toggle play/pause for current item
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    holder.playPause.setImageResource(android.R.drawable.ic_media_play)
                    mainHandler.removeCallbacks(tickRunnable)
                } else {
                    mediaPlayer?.start()
                    holder.playPause.setImageResource(android.R.drawable.ic_media_pause)
                    mainHandler.post(tickRunnable)
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
            stopPlayback()
        }
    }
    
    private fun stopPlayback() {
        mediaPlayer?.apply {
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        currentHolder?.playPause?.setImageResource(android.R.drawable.ic_media_play)
        currentHolder?.timer?.text = "0:00"
        currentHolder?.seekbar?.progress = 0
        currentItem = null
        currentHolder = null
        mainHandler.removeCallbacks(tickRunnable)
    }

    private val TAG = "RecordingsActivity" // or whatever your activity class name is

    private fun promptRename(item: RecordingItem) {
        val oldFile = item.file
        val oldName = oldFile.nameWithoutExtension

        // Inflate dialog view
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_file, null)
        val input = dialogView.findViewById<TextInputEditText>(R.id.file_name)

        input?.apply {
            setText(oldName)
            selectAll()
            // Make text white explicitly
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .show()

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = input?.text?.toString()?.trim()
            if (newName.isNullOrEmpty()) {
                input?.error = getString(R.string.invalid_name)
                return@setOnClickListener
            }
            if (newName == oldName) {
                alertDialog.dismiss()
                return@setOnClickListener
            }

            try {
                val newFile = File(oldFile.parentFile, "$newName.${oldFile.extension}")
                if (newFile.exists()) {
                    toast(R.string.file_already_exists)
                    return@setOnClickListener
                }

                var success = false

                // 1) Simple rename
                try {
                    success = oldFile.renameTo(newFile)
                } catch (_: Exception) {}

                // 2) Files.move (API 26+)
                if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val src = java.nio.file.Paths.get(oldFile.absolutePath)
                        val dst = java.nio.file.Paths.get(newFile.absolutePath)
                        java.nio.file.Files.move(src, dst, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                        success = newFile.exists()
                    } catch (_: Exception) {}
                }

                // 3) Copy+Delete fallback
                if (!success) {
                    try {
                        oldFile.inputStream().use { inputStream ->
                            newFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        success = oldFile.delete()
                    } catch (_: Exception) {}
                }

                if (success) {
                    val oldPath = oldFile.absolutePath
                    val newPath = newFile.absolutePath
                    if (UploadedRecordingsManager.getUploadedPaths(this).contains(oldPath)) {
                        UploadedRecordingsManager.removeUploadedPath(this, oldPath)
                        UploadedRecordingsManager.addUploadedPath(this, newPath)
                    }
                    // update file reference
                    item.file = newFile

                    // Refresh the list and update the adapter
                    val updatedList = loadRecordings()
                    (recyclerView.adapter as? RecordingsAdapter)?.updateItems(updatedList)
                    emptyView.beGoneIf(updatedList.isNotEmpty())

                    toast(R.string.file_renamed)
                    alertDialog.dismiss()
                } else {
                    toast(R.string.rename_failed)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }




    private fun promptDelete(item: RecordingItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.proceed_with_deletion)
            .setPositiveButton(R.string.yes) { _, _ ->
                deleteRecording(item)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun setupRecyclerView() {
        val items = loadRecordings()
        emptyView.beGoneIf(items.isNotEmpty())
        recyclerView.adapter = RecordingsAdapter(items, activeUploads, object : RecordingClickListener {
            override fun onPlayPauseClicked(item: RecordingItem, holder: RecordingViewHolder) {
                handlePlayPause(item, holder)
            }
            override fun onRenameRequested(item: RecordingItem) { promptRename(item) }
            override fun onDeleteRequested(item: RecordingItem) { promptDelete(item) }
            override fun onUploadRequested(item: RecordingItem) {
                uploadRecording(item)
            }
        })
    }

    private fun uploadRecording(item: RecordingItem) {
        val file = item.file
        if (!file.exists()) {
            toast("File not found!")
            return
        }

        if (item.isUploaded) {
            toast("This recording is already uploaded")
            return
        }

        val filePath = file.absolutePath
        if (item.isUploading) {
            toast("Upload already in progress")
            return
        }

        // Update UI state
        item.isUploading = true
        refreshList()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = RetrofitClient.uploadAudioFile(this@RecordingsActivity, Uri.fromFile(file))
                withContext(Dispatchers.Main) {
                    result.onSuccess { response ->
                        item.isUploaded = true
                        item.isUploading = false

                        response.call_details_id.let { callDetailsId ->
                            AuthHelper.saveCallDetailsId(this@RecordingsActivity, callDetailsId)
                            Log.d("Upload", "Saved call_details_id: $callDetailsId")
                            Toast.makeText(this@RecordingsActivity, "Saved call_details_id: $callDetailsId", Toast.LENGTH_SHORT).show()
                        }

                        UploadedRecordingsManager.addUploadedPath(this@RecordingsActivity, file.absolutePath)
                        toast("Recording uploaded successfully!")

                        lifecycleScope.launch(Dispatchers.IO) {
                            val analysisResult = RetrofitClient.triggerCallAnalysis(response)
                            withContext(Dispatchers.Main) {
                                analysisResult.onSuccess {
                                    Toast.makeText(this@RecordingsActivity, "Call analysis completed!", Toast.LENGTH_LONG).show()
                                    Log.d("Analysis", "Success: ${it.message}")
                                }.onFailure { e ->
                                    Toast.makeText(this@RecordingsActivity, "Analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e("Analysis", "Failed", e)
                                }
                            }
                        }
                    }.onFailure { e ->
                        item.isUploading = false
                        toast("Upload failed: ${e.message}")
                    }
                    refreshList() // Update UI to reflect the new state
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    item.isUploading = false
                    toast("Upload error: ${e.message}")
                    refreshList()
                }
            } finally {
                activeUploads.remove(filePath)
            }
        }
    }

}
