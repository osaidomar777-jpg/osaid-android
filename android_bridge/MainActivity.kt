package com.osaid.file.osaid_file

import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private val DOWNLOADER_CHANNEL = "com.osaid.file/downloader"
    private val PROGRESS_CHANNEL = "com.osaid.file/downloader_progress"
    private val BRIDGE_CHANNEL = "com.osaid.file/bridge"

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var eventSink: EventChannel.EventSink? = null
    @Volatile
    private var initialized = false
    @Volatile
    private var updatedOnce = false

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    YoutubeDL.getInstance().init(applicationContext)
                    FFmpeg.getInstance().init(applicationContext)
                    initialized = true
                }
            }
        }
        if (!updatedOnce) {
            updatedOnce = true
            executor.execute {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun scanPath(path: String) {
        try {
            MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
        } catch (_: Exception) {
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // قناة جسر USB والصلاحيات وتحديث MTP (MediaScanner)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BRIDGE_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "hasAllFilesPermission" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            result.success(Environment.isExternalStorageManager())
                        } else {
                            result.success(true)
                        }
                    }

                    "openAllFilesPermission" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:$packageName")
                                startActivity(intent)
                                result.success(true)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                startActivity(intent)
                                result.success(true)
                            }
                        } else {
                            result.success(true)
                        }
                    }

                    "scanFile" -> {
                        val path = call.argument<String>("path")
                        if (!path.isNullOrEmpty()) {
                            scanPath(path)
                        }
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }

        // قناة تقدم التنزيل المباشر
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, PROGRESS_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            })

        // قناة محمّل أسيد (Osaid Downloader - yt-dlp + FFmpeg)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DOWNLOADER_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "updateEngine" -> {
                        executor.execute {
                            try {
                                ensureInit()
                                val status = YoutubeDL.getInstance().updateYoutubeDL(
                                    applicationContext,
                                    YoutubeDL.UpdateChannel.STABLE
                                )
                                mainHandler.post { result.success(status?.name ?: "OK") }
                            } catch (e: Exception) {
                                mainHandler.post { result.error("UPDATE_ERR", e.message ?: "فشل التحديث", null) }
                            }
                        }
                    }

                    "analyze" -> {
                        val url = call.argument<String>("url") ?: ""
                        executor.execute {
                            try {
                                ensureInit()
                                val info = YoutubeDL.getInstance().getInfo(url)
                                val map = hashMapOf<String, Any>(
                                    "title" to (info.title ?: url),
                                    "site" to (info.extractor ?: ""),
                                    "duration" to (info.duration.toString())
                                )
                                mainHandler.post { result.success(map) }
                            } catch (e: Exception) {
                                mainHandler.post {
                                    result.success(
                                        hashMapOf(
                                            "title" to url,
                                            "site" to "رابط مباشر",
                                            "duration" to ""
                                        )
                                    )
                                }
                            }
                        }
                    }

                    "download" -> {
                        val id = call.argument<String>("id") ?: System.currentTimeMillis().toString()
                        val url = call.argument<String>("url") ?: ""
                        val quality = call.argument<String>("quality") ?: "best_mp4"

                        executor.execute {
                            try {
                                ensureInit()
                                val outDir = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    "OsaidFile/Downloads"
                                )
                                if (!outDir.exists()) outDir.mkdirs()

                                val beforeFiles = outDir.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()

                                val request = YoutubeDLRequest(url)
                                request.addOption("-o", "${outDir.absolutePath}/%(title).80s.%(ext)s")
                                request.addOption("--no-mtime")
                                request.addOption("--no-playlist")
                                request.addOption("--no-check-certificates")

                                when (quality) {
                                    "2160" -> {
                                        request.addOption("-f", "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "1440" -> {
                                        request.addOption("-f", "bestvideo[height<=1440]+bestaudio/best[height<=1440]/best")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "1080" -> {
                                        request.addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "720" -> {
                                        request.addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "480" -> {
                                        request.addOption("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]/best")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "mp3" -> {
                                        request.addOption("-x")
                                        request.addOption("--audio-format", "mp3")
                                    }
                                    "m4a" -> {
                                        request.addOption("-x")
                                        request.addOption("--audio-format", "m4a")
                                    }
                                    "flac" -> {
                                        request.addOption("-x")
                                        request.addOption("--audio-format", "flac")
                                    }
                                    "wav" -> {
                                        request.addOption("-x")
                                        request.addOption("--audio-format", "wav")
                                    }
                                    else -> {
                                        request.addOption("-f", "bestvideo+bestaudio/best")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                }

                                YoutubeDL.getInstance().execute(request, id) { progress, _, line ->
                                    mainHandler.post {
                                        eventSink?.success(
                                            hashMapOf(
                                                "id" to id,
                                                "progress" to progress.toDouble(),
                                                "line" to line
                                            )
                                        )
                                    }
                                }

                                val afterFiles = outDir.listFiles() ?: emptyArray()
                                var savedFile: String? = null
                                for (f in afterFiles) {
                                    if (!beforeFiles.contains(f.absolutePath)) {
                                        scanPath(f.absolutePath)
                                        savedFile = f.absolutePath
                                    }
                                }

                                mainHandler.post { result.success(savedFile ?: outDir.absolutePath) }
                            } catch (e: Exception) {
                                mainHandler.post {
                                    result.error("DOWNLOAD_ERR", e.message ?: "فشل التحميل", null)
                                }
                            }
                        }
                    }

                    "cancel" -> {
                        val id = call.argument<String>("id") ?: ""
                        try {
                            YoutubeDL.getInstance().destroyProcessById(id)
                        } catch (_: Exception) {
                        }
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }
    }
}
