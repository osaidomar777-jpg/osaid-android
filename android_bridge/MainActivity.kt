package com.osaid.file.osaid_file

import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private val DOWNLOADER_CHANNEL = "com.osaid.file/downloader"
    private val PROGRESS_CHANNEL = "com.osaid.file/downloader_progress"
    private val BRIDGE_CHANNEL = "com.osaid.file/bridge"

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var eventSink: EventChannel.EventSink? = null
    private val initLock = Any()

    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
            initialized = true
        }
    }

    private fun scanPath(path: String) {
        try {
            MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
        } catch (_: Exception) {
        }
    }

    /// حفظ أي ملف مُنزّل إلى MediaStore (يعمل على أندرويد 10-15 بدون الحاجة لصلاحية All Files Access)
    /// ويظهر فوراً في المعرض والموسيقى ومدير الملفات و MTP
    private fun saveFileToPublicStorage(sourceFile: File): String {
        val name = sourceFile.name
        val ext = sourceFile.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "mp4", "mkv", "webm", "mov" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

        val isVideo = mime.startsWith("video/")
        val isAudio = mime.startsWith("audio/")
        val isImage = mime.startsWith("image/")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val (collection, relPath) = when {
                isVideo -> Pair(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_MOVIES}/OsaidFile")
                isAudio -> Pair(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_MUSIC}/OsaidFile")
                isImage -> Pair(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_PICTURES}/OsaidFile")
                else -> Pair(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_DOWNLOADS}/OsaidFile")
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "$relPath/$name"
            }
        }

        // Fallback لأندرويد 9 وما قبله أو إذا تعثر MediaStore
        val outDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "OsaidFile/Downloads"
        )
        if (!outDir.exists()) outDir.mkdirs()
        val dest = File(outDir, name)
        sourceFile.copyTo(dest, overwrite = true)
        scanPath(dest.absolutePath)
        return dest.absolutePath
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // تهيئة المحرك في الخلفية فور تشغيل التطبيق
        executor.execute {
            try {
                ensureInit()
            } catch (_: Exception) {
            }
        }

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

                    "saveToPublic" -> {
                        val path = call.argument<String>("path") ?: ""
                        executor.execute {
                            try {
                                val f = File(path)
                                if (!f.exists()) {
                                    mainHandler.post { result.error("NOT_FOUND", "الملف غير موجود", null) }
                                    return@execute
                                }
                                val saved = saveFileToPublicStorage(f)
                                mainHandler.post { result.success(saved) }
                            } catch (e: Exception) {
                                mainHandler.post { result.error("SAVE_ERR", e.message, null) }
                            }
                        }
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
                    "engineInfo" -> {
                        executor.execute {
                            try {
                                ensureInit()
                                val ver = YoutubeDL.getInstance().version(applicationContext) ?: "unknown"
                                mainHandler.post {
                                    result.success(hashMapOf("ready" to true, "version" to ver))
                                }
                            } catch (e: Exception) {
                                mainHandler.post {
                                    result.success(hashMapOf("ready" to false, "version" to (e.message ?: "error")))
                                }
                            }
                        }
                    }

                    "updateEngine" -> {
                        executor.execute {
                            try {
                                ensureInit()
                                val status = synchronized(initLock) {
                                    YoutubeDL.getInstance().updateYoutubeDL(
                                        applicationContext,
                                        YoutubeDL.UpdateChannel.STABLE
                                    )
                                }
                                val ver = YoutubeDL.getInstance().version(applicationContext) ?: ""
                                mainHandler.post { result.success("${status?.name ?: "OK"} ($ver)") }
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
                                val request = YoutubeDLRequest(url)
                                request.addOption("--no-check-certificates")
                                request.addOption("--geo-bypass")
                                val info = YoutubeDL.getInstance().getInfo(request)
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
                                            "site" to "جاري المحاولة...",
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
                            val tempDir = File(cacheDir, "osaid_dl/$id")
                            try {
                                ensureInit()
                                if (tempDir.exists()) tempDir.deleteRecursively()
                                tempDir.mkdirs()

                                val request = YoutubeDLRequest(url)
                                request.addOption("-o", "${tempDir.absolutePath}/%(title).70s.%(ext)s")
                                request.addOption("--no-mtime")
                                request.addOption("--no-playlist")
                                request.addOption("--no-check-certificates")
                                request.addOption("--geo-bypass")
                                request.addOption("--restrict-filenames")

                                when (quality) {
                                    "2160" -> {
                                        request.addOption("-f", "bv*[height<=2160]+ba/b[height<=2160]/b")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "1440" -> {
                                        request.addOption("-f", "bv*[height<=1440]+ba/b[height<=1440]/b")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "1080" -> {
                                        request.addOption("-f", "bv*[height<=1080]+ba/b[height<=1080]/b")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "720" -> {
                                        request.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b")
                                        request.addOption("--merge-output-format", "mp4")
                                    }
                                    "480" -> {
                                        request.addOption("-f", "bv*[height<=480]+ba/b[height<=480]/b")
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
                                        request.addOption("-f", "bv*+ba/b")
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

                                val downloadedFiles = tempDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()
                                if (downloadedFiles.isEmpty()) {
                                    throw Exception("لم ينتج أي ملف بعد التحميل")
                                }

                                val savedPaths = mutableListOf<String>()
                                for (f in downloadedFiles) {
                                    savedPaths.add(saveFileToPublicStorage(f))
                                }
                                tempDir.deleteRecursively()

                                mainHandler.post { result.success(savedPaths.first()) }
                            } catch (e: Exception) {
                                tempDir.deleteRecursively()
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
