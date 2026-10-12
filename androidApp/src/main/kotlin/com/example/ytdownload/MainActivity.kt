package com.example.ytdownload

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                VideoSaverScreen(this)
            }
        }
    }
}

@Composable
private fun VideoSaverScreen(context: Context) {
    var url by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("720p") }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember {
        mutableStateOf("Paste a YouTube link to open it, or a permitted direct video-file URL to download.")
    }
    var downloading by remember { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val qualities = listOf("360p", "480p", "720p", "1080p")

    DisposableEffect(Unit) {
        onDispose { handler.removeCallbacksAndMessages(null) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "▶ Video Saver",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Open YouTube videos or save video files you have permission to download.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Video URL") },
                placeholder = { Text("https://example.com/video.mp4") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(14.dp)
            )

            Text("Select quality", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                qualities.forEach { item ->
                    FilterChip(
                        selected = quality == item,
                        onClick = { quality = item },
                        label = { Text(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "Selected: $quality. Quality is a preference only; the source must provide a matching file or stream.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    val parsed = Uri.parse(url.trim())
                    val host = parsed.host?.lowercase().orEmpty()
                    val isYouTube = host == "youtube.com" ||
                        host.endsWith(".youtube.com") ||
                        host == "youtu.be" ||
                        host.endsWith(".youtu.be")

                    if (parsed.scheme !in listOf("http", "https") || host.isBlank()) {
                        message = "Enter a valid HTTP or HTTPS URL."
                    } else if (isYouTube) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, parsed))
                            message = "Opened YouTube. Use YouTube's authorized offline-download options where available."
                        } catch (_: Exception) {
                            message = "Could not open this YouTube link."
                        }
                    } else {
                        val path = parsed.path.orEmpty().lowercase()
                        val extension = path.substringAfterLast('.', "")
                        if (extension !in listOf("mp4", "webm", "m4v", "mov")) {
                            message = "This starter accepts direct video-file links ending in .mp4, .webm, .m4v, or .mov."
                        } else {
                            try {
                                val filename = "Video_${System.currentTimeMillis()}.$extension"
                                val request = DownloadManager.Request(parsed)
                                    .setTitle(filename)
                                    .setDescription("Downloading permitted video")
                                    .setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    )
                                    .setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_MOVIES,
                                        filename
                                    )
                                    .setAllowedOverMetered(true)
                                    .setAllowedOverRoaming(false)

                                val manager = context.getSystemService(
                                    Context.DOWNLOAD_SERVICE
                                ) as DownloadManager
                                val downloadId = manager.enqueue(request)
                                downloading = true
                                progress = 0f
                                message = "Download started…"

                                val poller = object : Runnable {
                                    override fun run() {
                                        val query = DownloadManager.Query().setFilterById(downloadId)
                                        val cursor = manager.query(query)
                                        if (cursor == null) {
                                            downloading = false
                                            message = "Could not check download status."
                                            return
                                        }
                                        cursor.use {
                                            if (!it.moveToFirst()) {
                                                downloading = false
                                                message = "Download task not found."
                                                return
                                            }
                                            val status = it.getInt(
                                                it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                                            )
                                            when (status) {
                                                DownloadManager.STATUS_SUCCESSFUL -> {
                                                    progress = 1f
                                                    downloading = false
                                                    message = "Saved in Movies. Check your Gallery or file manager."
                                                }
                                                DownloadManager.STATUS_FAILED -> {
                                                    downloading = false
                                                    val reason = it.getInt(
                                                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                                                    )
                                                    message = "Download failed (reason $reason). Check the URL and try again."
                                                }
                                                else -> {
                                                    val total = it.getLong(
                                                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                                    )
                                                    val soFar = it.getLong(
                                                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                                    )
                                                    if (total > 0L) {
                                                        progress = (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                                    }
                                                    message = "Downloading…"
                                                    handler.postDelayed(this, 500)
                                                }
                                            }
                                        }
                                    }
                                }
                                handler.post(poller)
                            } catch (e: Exception) {
                                downloading = false
                                message = "Could not start download: ${e.message ?: "unknown error"}"
                            }
                        }
                    }
                },
                enabled = !downloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (downloading) "Downloading…" else "Open / Download")
            }

            if (downloading || progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(progress * 100).toInt()}%")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(message, modifier = Modifier.padding(16.dp))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Privacy: this app does not ask for broad gallery access. Android's DownloadManager saves permitted files in the public Movies folder.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "YouTube note: YouTube's official Data API does not provide a general-purpose MP4 download endpoint. This app opens YouTube links in the official app/browser instead.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
