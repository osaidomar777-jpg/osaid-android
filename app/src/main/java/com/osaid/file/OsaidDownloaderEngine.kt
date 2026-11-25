package com.osaid.file

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

enum class DownloadQuality(val label: String, val isAudioOnly: Boolean) {
    BEST_MP4("أفضل جودة (MP4)", false),
    HD_1080("فيديو 1080p", false),
    HD_720("فيديو 720p", false),
    SD_480("فيديو 480p", false),
    AUDIO_MP3("صوت فقط (MP3)", true),
    AUDIO_M4A("صوت فقط (M4A)", true)
}

data class DownloadTaskItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val siteName: String,
    val quality: DownloadQuality,
    val statusText: String = "في الانتظار",
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val outputPath: String? = null
)

data class DownloadOptions(
    val quality: DownloadQuality = DownloadQuality.BEST_MP4,
    val embedMetadataAndThumb: Boolean = true,
    val downloadSubtitles: Boolean = false,
    val sponsorBlockSkip: Boolean = false,
    val clipStart: String = "",
    val clipEnd: String = ""
)

object OsaidDownloaderEngine {

    fun parseUrls(rawInput: String): List<String> {
        return rawInput
            .split(Regex("[\\s\\r\\n]+"))
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()
    }

    fun detectSiteName(url: String): String {
        val lower = url.lowercase()
        return when {
            "youtube.com" in lower || "youtu.be" in lower -> "YouTube"
            "tiktok.com" in lower -> "TikTok"
            "instagram.com" in lower -> "Instagram"
            "facebook.com" in lower || "fb.watch" in lower -> "Facebook"
            "twitter.com" in lower || "x.com" in lower -> "X / Twitter"
            "pinterest.com" in lower -> "Pinterest"
            "snapchat.com" in lower -> "Snapchat"
            "reddit.com" in lower -> "Reddit"
            "soundcloud.com" in lower -> "SoundCloud"
            "vimeo.com" in lower -> "Vimeo"
            "twitch.tv" in lower -> "Twitch"
            "t.me" in lower -> "Telegram"
            else -> runCatching {
                java.net.URI(url).host?.removePrefix("www.") ?: "الإنترنت"
            }.getOrDefault("الإنترنت")
        }
    }

    fun getOutputDirectory(audioOnly: Boolean): File {
        val base = if (audioOnly) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val folder = File(base, "Osaid Downloader")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun download(
        context: Context,
        processId: String,
        url: String,
        options: DownloadOptions,
        onProgress: (Float, Long, String) -> Unit
    ): String {
        if (!OsaidApp.isDownloaderReady) {
            throw IllegalStateException(
                OsaidApp.downloaderInitError ?: "محرك التنزيل لا يزال قيد التهيئة، حاول بعد ثانيتين."
            )
        }

        val outDir = getOutputDirectory(options.quality.isAudioOnly)
        val beforeFiles = outDir.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-warnings")
            addOption("--no-playlist")
            addOption("--retries", "5")
            addOption("--fragment-retries", "5")
            addOption("--concurrent-fragments", "4")
            addOption("-o", "${outDir.absolutePath}/%(title).80B [%(id)s].%(ext)s")

            when (options.quality) {
                DownloadQuality.BEST_MP4 -> {
                    addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
                DownloadQuality.HD_1080 -> {
                    addOption("-f", "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
                DownloadQuality.HD_720 -> {
                    addOption("-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
                DownloadQuality.SD_480 -> {
                    addOption("-f", "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
                DownloadQuality.AUDIO_MP3 -> {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
                DownloadQuality.AUDIO_M4A -> {
                    addOption("-x")
                    addOption("--audio-format", "m4a")
                    addOption("--audio-quality", "0")
                }
            }

            if (options.embedMetadataAndThumb) {
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
            }

            if (options.downloadSubtitles && !options.quality.isAudioOnly) {
                addOption("--write-subs")
                addOption("--write-auto-subs")
                addOption("--sub-langs", "ar.*,en.*")
                addOption("--embed-subs")
            }

            if (options.sponsorBlockSkip) {
                addOption("--sponsorblock-remove", "sponsor")
            }

            val start = options.clipStart.trim()
            val end = options.clipEnd.trim()
            if (start.isNotEmpty() || end.isNotEmpty()) {
                val range = "*${start.ifEmpty { "0:00" }}-${end.ifEmpty { "inf" }}"
                addOption("--download-sections", range)
            }
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
            val pct = (progress / 100f).coerceIn(0f, 1f)
            onProgress(pct, etaInSeconds, line)
        }

        val newestFile = outDir.listFiles()
            ?.filter { it.isFile && it.absolutePath !in beforeFiles }
            ?.maxByOrNull { it.lastModified() }
            ?: outDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }

        if (newestFile != null) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(newestFile.absolutePath),
                null,
                null
            )
            return newestFile.absolutePath
        }

        return outDir.absolutePath
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    fun updateEngine(context: Context): String {
        val status = YoutubeDL.getInstance().updateYoutubeDL(
            context,
            YoutubeDL.UpdateChannel.STABLE
        )
        return when (status) {
            YoutubeDL.UpdateStatus.DONE -> "تم تحديث محرك التنزيل (yt-dlp) إلى أحدث إصدار!"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "محرك التنزيل محدث بالفعل لأحدث إصدار."
            else -> "حالة التحديث: $status"
        }
    }
}
