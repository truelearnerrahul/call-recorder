package org.fossify.phone.activities

import android.content.ClipData
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu

class RecordingsActivity : SimpleActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var currentItem: RecordingItem? = null
    private var currentHolder: RecordingViewHolder? = null

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

        // Handle import via share intents
        val importedCount = handleShareIntentIfAny(intent)
        if (importedCount > 0) {
            toast(getString(R.string.import_completed, importedCount))
        }

        val items = loadRecordings()
        emptyView.beGoneIf(items.isNotEmpty())

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RecordingsAdapter(items, object : RecordingClickListener {
            override fun onPlayPauseClicked(item: RecordingItem, holder: RecordingViewHolder) {
                handlePlayPause(item, holder)
            }
            override fun onRenameRequested(item: RecordingItem) {
                promptRename(item)
            }
            override fun onDeleteRequested(item: RecordingItem) {
                promptDelete(item)
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val importedCount = handleShareIntentIfAny(intent)
        if (importedCount >= 0) {
            val items = loadRecordings()
            emptyView.beGoneIf(items.isNotEmpty())
            recyclerView.adapter = RecordingsAdapter(items, object : RecordingClickListener {
                override fun onPlayPauseClicked(item: RecordingItem, holder: RecordingViewHolder) {
                    handlePlayPause(item, holder)
                }
                override fun onRenameRequested(item: RecordingItem) { promptRename(item) }
                override fun onDeleteRequested(item: RecordingItem) { promptDelete(item) }
            })
            if (importedCount > 0) toast(getString(R.string.import_completed, importedCount))
        }
    }

    private fun handleShareIntentIfAny(intent: Intent?): Int {
        if (intent == null) return -1
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    if (importOne(uri)) 1 else 0
                } else 0
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: extractUrisFromClipData(intent.clipData)
                var count = 0
                uris.forEach { if (importOne(it)) count++ }
                count
            }

            else -> -1
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

            val name = queryDisplayName(uri) ?: "shared_${System.currentTimeMillis()}.m4a"
            val safeName = ensureExtension(name, defaultExt = ".m4a")
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
        } catch (_: Exception) { null }
    }

    private fun loadRecordings(): List<RecordingItem> {
        val root = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        val exts = setOf(".ogg", ".m4a", ".mp3", ".aac", ".wav", ".3gp")
        val files = root.listFiles { f ->
            f.isFile && exts.any { ext -> f.name.endsWith(ext, ignoreCase = true) }
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
        return files.map { file ->
            val duration = readDuration(file)
            val isShared = file.name.startsWith("Shared_", ignoreCase = true)
            RecordingItem(file, duration, isShared)
        }
    }

    private fun readDuration(file: File): Long {
        return try {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(file.absolutePath)
                val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durStr?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) { 0L }
    }

    private fun handlePlayPause(item: RecordingItem, holder: RecordingViewHolder) {
        val mp = mediaPlayer
        if (currentItem == item && mp != null) {
            if (mp.isPlaying) {
                mp.pause()
                holder.playPause.setImageResource(R.drawable.ic_play_vector)
            } else {
                mp.start()
                holder.playPause.setImageResource(R.drawable.ic_pause_vector)
                mainHandler.removeCallbacks(tickRunnable)
                mainHandler.post(tickRunnable)
            }
            return
        }

        // New item selected
        releasePlayer(resetUI = true)
        currentItem = item
        currentHolder = holder
        mediaPlayer = MediaPlayer()
        try {
            mediaPlayer!!.setDataSource(item.file.absolutePath)
            mediaPlayer!!.setOnPreparedListener {
                // Initialize seekbar max to duration and reset progress
                holder.seekbar.max = it.duration
                holder.seekbar.progress = 0

                it.start()
                holder.playPause.setImageResource(R.drawable.ic_pause_vector)
                holder.timer.text = formatMs(0)
                mainHandler.removeCallbacks(tickRunnable)
                mainHandler.post(tickRunnable)
            }
            mediaPlayer!!.setOnCompletionListener {
                holder.playPause.setImageResource(R.drawable.ic_play_vector)
                holder.timer.text = formatMs(item.duration)
                holder.seekbar.progress = holder.seekbar.max
                mainHandler.removeCallbacks(tickRunnable)
                currentItem = null
                currentHolder = null
                releasePlayer(resetUI = false)
            }
            mediaPlayer!!.prepareAsync()

            // Hook up seekbar listener for scrubbing
            holder.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var userSeeking = false
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        // Update timer while dragging
                        holder.timer.text = formatMs(progress.toLong())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = false
                    val position = seekBar?.progress ?: 0
                    try {
                        mediaPlayer?.seekTo(position)
                    } catch (_: Exception) {}
                }
            })
        } catch (_: Exception) {
            holder.playPause.setImageResource(R.drawable.ic_play_vector)
            mainHandler.removeCallbacks(tickRunnable)
            releasePlayer(resetUI = false)
        }
    }

    private fun releasePlayer(resetUI: Boolean) {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        if (resetUI) {
            currentHolder?.playPause?.setImageResource(R.drawable.ic_play_vector)
            currentHolder?.timer?.text = formatMs(currentItem?.duration ?: 0L)
            currentHolder?.seekbar?.progress = 0
        }
        currentHolder = null
        currentItem = null
        mainHandler.removeCallbacks(tickRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer(resetUI = false)
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    // Prompt user to rename the selected recording
    private fun promptRename(item: RecordingItem) {
        val ctx = this
        val input = EditText(ctx).apply {
            setText(item.file.nameWithoutExtension)
            setSelection(text.length)
        }
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.rename))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newBase = input.text.toString().trim()
                if (newBase.isEmpty()) return@setPositiveButton
                val old = item.file
                val ext = old.extension.let { if (it.isNotEmpty()) ".${it}" else "" }
                val target = uniqueFile(old.parentFile ?: filesDir, newBase + ext)
                val success = try { old.renameTo(target) } catch (_: Exception) { false }
                if (success) {
                    toast(getString(R.string.renamed))
                    refreshList()
                } else {
                    toast(getString(R.string.unknown_error_occurred))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Prompt user to confirm deletion
    private fun promptDelete(item: RecordingItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.proceed_with_deletion))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Stop playback if deleting current
                if (currentItem?.file == item.file) {
                    releasePlayer(resetUI = true)
                }
                val success = try { item.file.delete() } catch (_: Exception) { false }
                if (success) {
                    toast(getString(R.string.deleted))
                    refreshList()
                } else {
                    toast(getString(R.string.unknown_error_occurred))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshList() {
        val items = loadRecordings()
        emptyView.beGoneIf(items.isNotEmpty())
        recyclerView.adapter = RecordingsAdapter(items, object : RecordingClickListener {
            override fun onPlayPauseClicked(item: RecordingItem, holder: RecordingViewHolder) {
                handlePlayPause(item, holder)
            }
            override fun onRenameRequested(item: RecordingItem) { promptRename(item) }
            override fun onDeleteRequested(item: RecordingItem) { promptDelete(item) }
        })
    }
}

private data class RecordingItem(val file: File, val duration: Long, val isShared: Boolean)

private interface RecordingClickListener {
    fun onPlayPauseClicked(item: RecordingItem, holder: RecordingViewHolder)
    fun onRenameRequested(item: RecordingItem)
    fun onDeleteRequested(item: RecordingItem)
}

private class RecordingsAdapter(
    private val items: List<RecordingItem>,
    private val listener: RecordingClickListener
) : RecyclerView.Adapter<RecordingViewHolder>() {
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

        holder.playPause.setOnClickListener { listener.onPlayPauseClicked(item, holder) }

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
}

class RecordingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.recording_title)
    val subtitle: TextView = view.findViewById(R.id.recording_subtitle)
    val timer: TextView = view.findViewById(R.id.recording_timer)
    val playPause: ImageButton = view.findViewById(R.id.recording_play_pause)
    val seekbar: SeekBar = view.findViewById(R.id.recording_seekbar)
    val badge: TextView = view.findViewById(R.id.recording_badge)
    val more: ImageButton = view.findViewById(R.id.recording_more)
}
