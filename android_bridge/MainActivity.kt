package com.osaid.file

import android.app.Application
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.yausername.aria2c.Aria2c
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
    private val CHANNEL = "com.osaid.file/downloader"
    private val PROGRESS_CHANNEL = "com.osaid.file/downloader_progress"
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var eventSink: EventChannel.EventSink? = null
    private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                Aria2c.getInstance().init(applicationContext)
                initialized = true
            } catch (_: Exception) {
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, PROGRESS_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            })

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
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
                                    result.success(hashMapOf("title" to url, "site" to "رابط مباشر", "duration" to ""))
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

                                val request = YoutubeDLRequest(url)
                                request.addOption("-o", "${outDir.absolutePath}/%(title)s.%(ext)s")
                                request.addOption("--no-mtime")

                                when (quality) {
                                    "2160" -> request.addOption("-f", "bestvideo[height<=2160]+bestaudio/best[height<=2160]")
                                    "1440" -> request.addOption("-f", "bestvideo[height<=1440]+bestaudio/best[height<=1440]")
                                    "1080" -> request.addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]")
                                    "720" -> request.addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]")
                                    "480" -> request.addOption("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]")
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
                                mainHandler.post { result.success(outDir.absolutePath) }
                            } catch (e: Exception) {
                                mainHandler.post { result.error("DOWNLOAD_ERR", e.message ?: "فشل التحميل", null) }
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
