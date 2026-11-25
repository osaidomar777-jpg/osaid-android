package com.osaid.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MediaKind {
    IMAGE,
    VIDEO
}

data class IncomingMediaItem(
    val id: String,
    val name: String,
    val path: String,
    val kind: MediaKind,
    val sizeBytes: Long
) {
    val sizeText: String get() = formatBytes(sizeBytes)
}

data class TextEntryItem(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long
) {
    val sizeText: String get() = formatBytes(sizeBytes)
}

data class LogItem(
    val id: Long = System.nanoTime(),
    val text: String,
    val ok: Boolean
)

data class ScanSnapshot(
    val media: List<IncomingMediaItem>,
    val texts: List<TextEntryItem>,
    val outboxCount: Int,
    val incomingFolderPath: String
)

data class ImportSummary(
    val imported: Int,
    val skippedDuplicates: Int,
    val failed: Int,
    val logs: List<LogItem>
)

object MediaImporter {

    private val imageExtensions = setOf(
        "heic", "heif", "jpg", "jpeg", "png", "gif", "tif", "tiff", "webp", "bmp"
    )
    private val videoExtensions = setOf(
        "mov", "mp4", "m4v", "3gp", "avi", "mkv", "webm"
    )
    private val textExtensions = setOf(
        "txt", "md", "csv", "json", "log", "xml", "yml", "yaml", "ini", "html", "htm", "rtf"
    )

    private const val PREFS_NAME = "osaid_file_prefs"
    private const val KEY_FINGERPRINTS = "imported_fingerprints"
    private const val CHUNK_BYTES = 262_144L

    fun getPrimaryIncomingFolder(context: Context): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val publicDir = File(docs, "Osaid File")
        return try {
            if (!publicDir.exists()) publicDir.mkdirs()
            if (publicDir.exists() && publicDir.canRead()) publicDir
            else ensureAppIncomingFolder(context)
        } catch (_: Throwable) {
            ensureAppIncomingFolder(context)
        }
    }

    fun ensureAppIncomingFolder(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "Incoming")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getOutboxFolder(context: Context): File {
        val primary = getPrimaryIncomingFolder(context)
        val outbox = File(primary, "إلى الكمبيوتر")
        if (!outbox.exists()) outbox.mkdirs()
        return outbox
    }

    fun scan(context: Context): ScanSnapshot {
        val roots = linkedSetOf(
            getPrimaryIncomingFolder(context),
            ensureAppIncomingFolder(context)
        )

        val outboxName = "إلى الكمبيوتر"
        val media = mutableListOf<IncomingMediaItem>()
        val texts = mutableListOf<TextEntryItem>()
        val seenPaths = mutableSetOf<String>()

        for (root in roots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") && dir.name != outboxName }
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { file ->
                    val canonical = file.absolutePath
                    if (!seenPaths.add(canonical)) return@forEach

                    val ext = file.extension.lowercase(Locale.US)
                    val size = file.length()
                    when {
                        ext in imageExtensions -> media.add(
                            IncomingMediaItem(
                                id = canonical,
                                name = file.name,
                                path = canonical,
                                kind = MediaKind.IMAGE,
                                sizeBytes = size
                            )
                        )
                        ext in videoExtensions -> media.add(
                            IncomingMediaItem(
                                id = canonical,
                                name = file.name,
                                path = canonical,
                                kind = MediaKind.VIDEO,
                                sizeBytes = size
                            )
                        )
                        ext in textExtensions -> texts.add(
                            TextEntryItem(
                                id = canonical,
                                name = file.name,
                                path = canonical,
                                sizeBytes = size,
                                modifiedAt = file.lastModified()
                            )
                        )
                    }
                }
        }

        val outboxFiles = getOutboxFolder(context).listFiles()?.count { it.isFile } ?: 0
        return ScanSnapshot(
            media = media.sortedBy { it.name.lowercase(Locale.getDefault()) },
            texts = texts.sortedByDescending { it.modifiedAt },
            outboxCount = outboxFiles,
            incomingFolderPath = getPrimaryIncomingFolder(context).absolutePath
        )
    }

    fun copyPickedFilesToIncoming(context: Context, uris: List<Uri>): Int {
        val targetDir = ensureAppIncomingFolder(context)
        var copied = 0
        for (uri in uris) {
            val fileName = resolveDisplayName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val dest = uniqueFile(targetDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
                copied++
            }
        }
        return copied
    }

    fun importAllMedia(
        context: Context,
        items: List<IncomingMediaItem>,
        onProgress: (Float, String) -> Unit
    ): ImportSummary {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val known = prefs.getStringSet(KEY_FINGERPRINTS, emptySet())?.toMutableSet() ?: mutableSetOf()

        var imported = 0
        var skipped = 0
        var failed = 0
        val logs = mutableListOf<LogItem>()
        val total = items.size.coerceAtLeast(1)

        items.forEachIndexed { index, item ->
            onProgress((index.toFloat() / total), item.name)
            val file = File(item.path)
            if (!file.exists()) {
                failed++
                logs.add(LogItem(text = "تعذر العثور على ${item.name}", ok = false))
                return@forEachIndexed
            }

            val fp = computeFingerprint(file)
            if (fp.isNotEmpty() && known.contains(fp)) {
                skipped++
                logs.add(LogItem(text = "تم تخطي ${item.name} — مستورد مسبقاً", ok = true))
                runCatching { file.delete() }
                return@forEachIndexed
            }

            val ok = insertIntoMediaStore(context, file, item.kind)
            if (ok) {
                imported++
                if (fp.isNotEmpty()) known.add(fp)
                runCatching { file.delete() }
                logs.add(LogItem(text = "تم استيراد ${item.name} إلى المعرض", ok = true))
            } else {
                failed++
                logs.add(LogItem(text = "فشل استيراد ${item.name}", ok = false))
            }
        }

        prefs.edit().putStringSet(KEY_FINGERPRINTS, known).apply()
        onProgress(1f, "")
        return ImportSummary(imported = imported, skippedDuplicates = skipped, failed = failed, logs = logs)
    }

    private fun insertIntoMediaStore(context: Context, file: File, kind: MediaKind): Boolean {
        val resolver = context.contentResolver
        val ext = file.extension.lowercase(Locale.US)
        val mime = when (kind) {
            MediaKind.IMAGE -> when (ext) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic", "heif" -> "image/heic"
                else -> "image/jpeg"
            }
            MediaKind.VIDEO -> when (ext) {
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                else -> "video/mp4"
            }
        }

        val collection = if (kind == MediaKind.IMAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val relativeFolder = if (kind == MediaKind.IMAGE) {
            "${Environment.DIRECTORY_PICTURES}/Osaid File"
        } else {
            "${Environment.DIRECTORY_MOVIES}/Osaid File"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val itemUri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            true
        } catch (_: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            false
        }
    }

    fun readText(item: TextEntryItem): String {
        val file = File(item.path)
        if (!file.exists()) return "الملف غير موجود."
        if (file.length() > 2_097_152L) {
            return "الملف كبير جداً للعرض (${item.sizeText}). انقله إلى الكمبيوتر لقراءته."
        }
        return runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { "تعذر قراءة محتوى الملف." }
    }

    fun saveNewText(context: Context, text: String): File {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "النص فارغ" }
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val folder = getPrimaryIncomingFolder(context)
        val file = uniqueFile(folder, "نص-$stamp.txt")
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    fun deleteText(item: TextEntryItem): Boolean {
        return runCatching { File(item.path).delete() }.getOrDefault(false)
    }

    fun sendTextToComputerOutbox(context: Context, text: String, baseName: String? = null): File {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "لا يوجد نص للإرسال" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val cleanBase = (baseName ?: "text")
            .substringBeforeLast('.')
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
            .take(24)
            .ifBlank { "text" }
        val file = uniqueFile(getOutboxFolder(context), "${cleanBase}_$stamp.txt")
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    fun sendTextViaLanBridge(bridgeBaseUrl: String, text: String): String {
        val cleanUrl = bridgeBaseUrl.trim().trimEnd('/')
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            "أدخل رابط جسر الكمبيوتر كاملاً مثل http://192.168.1.5:5050"
        }

        val url = URL("$cleanUrl/api/text")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7000
            readTimeout = 7000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }

        val body = "text=" + URLEncoder.encode(text, "UTF-8")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299) {
            return "تم إرسال النص إلى الكمبيوتر عبر Wi-Fi بنجاح."
        }
        throw IllegalStateException("استجاب جسر الكمبيوتر برمز خطأ HTTP $code")
    }

    fun computeFingerprint(file: File): String {
        return runCatching {
            val size = file.length()
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(size.toString().toByteArray(Charsets.UTF_8))

            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(CHUNK_BYTES.toInt())
                val headRead = raf.read(buffer)
                if (headRead > 0) digest.update(buffer, 0, headRead)

                if (size > CHUNK_BYTES * 2) {
                    raf.seek(size - CHUNK_BYTES)
                    val tailRead = raf.read(buffer)
                    if (tailRead > 0) digest.update(buffer, 0, tailRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uniqueFile(folder: File, fileName: String): File {
        if (!folder.exists()) folder.mkdirs()
        var candidate = File(folder, fileName)
        if (!candidate.exists()) return candidate

        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        for (i in 2..9999) {
            candidate = File(folder, "$base ($i)$ext")
            if (!candidate.exists()) return candidate
        }
        return File(folder, "${base}_${System.currentTimeMillis()}$ext")
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 بايت"
    val units = arrayOf("بايت", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return if (idx == 0) "$bytes بايت" else String.format(Locale.US, "%.1f %s", value, units[idx])
}
