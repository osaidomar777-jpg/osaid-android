package com.osaid.file

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AppTab(val title: String) {
    DOWNLOADER("التنزيل الخاص بأسيد"),
    MEDIA("استيراد الوسائط"),
    TEXTS("النصوص والكمبيوتر")
}

data class MainUiState(
    val activeTab: AppTab = AppTab.DOWNLOADER,
    val hasMediaPermission: Boolean = false,
    val mediaItems: List<IncomingMediaItem> = emptyList(),
    val textFiles: List<TextEntryItem> = emptyList(),
    val outboxCount: Int = 0,
    val incomingFolderPath: String = "",
    val isScanning: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val importCurrentName: String = "",
    val importSummary: String = "",
    val logs: List<LogItem> = emptyList(),
    val bridgeUrl: String = "",
    val statusBanner: String = "",
    // حالة التنزيل الخاص بأسيد
    val urlsInput: String = "",
    val downloadOptions: DownloadOptions = DownloadOptions(),
    val downloadQueue: List<DownloadTaskItem> = emptyList(),
    val isDownloading: Boolean = false,
    val activeProcessId: String? = null,
    val isUpdatingEngine: Boolean = false
) {
    val totalMediaSizeText: String
        get() = formatBytes(mediaItems.sumOf { it.sizeBytes })
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasMediaPermission = granted) }
    }

    fun setSharedUrl(url: String) {
        if (url.isBlank()) return
        _uiState.update { state ->
            val updated = if (state.urlsInput.isBlank()) url else "${state.urlsInput.trim()}\n$url"
            state.copy(activeTab = AppTab.DOWNLOADER, urlsInput = updated)
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = "") }
    }

    fun scanIncoming() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true) }

        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                MediaImporter.scan(getApplication())
            }
            _uiState.update {
                it.copy(
                    mediaItems = snap.media,
                    textFiles = snap.texts,
                    outboxCount = snap.outboxCount,
                    incomingFolderPath = snap.incomingFolderPath,
                    isScanning = false
                )
            }
        }
    }

    fun addPickedFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                MediaImporter.copyPickedFilesToIncoming(getApplication(), uris)
            }
            _uiState.update { it.copy(statusBanner = "تمت إضافة $count ملف إلى قائمة الاستيراد.") }
            scanIncoming()
        }
    }

    fun importAllMedia() {
        val items = _uiState.value.mediaItems
        if (items.isEmpty() || _uiState.value.isImporting) return

        _uiState.update {
            it.copy(
                isImporting = true,
                importProgress = 0f,
                importSummary = "",
                importCurrentName = ""
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaImporter.importAllMedia(getApplication(), items) { prog, name ->
                    _uiState.update {
                        it.copy(importProgress = prog, importCurrentName = name)
                    }
                }
            }

            val summary = buildString {
                append("تم استيراد ${result.imported} من ${items.size}")
                if (result.skippedDuplicates > 0) append(" — تم تخطي ${result.skippedDuplicates}")
                if (result.failed > 0) append(" — فشل ${result.failed}")
            }

            _uiState.update {
                it.copy(
                    isImporting = false,
                    importProgress = 1f,
                    importCurrentName = "",
                    importSummary = summary,
                    logs = (result.logs + it.logs).take(60)
                )
            }
            scanIncoming()
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList(), importSummary = "") }
    }

    fun saveNewText(text: String) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { MediaImporter.saveNewText(getApplication(), text) }
            }
            res.onSuccess {
                _uiState.update { it.copy(statusBanner = "تم حفظ النص بنجاح.") }
                scanIncoming()
            }.onFailure { err ->
                _uiState.update { it.copy(statusBanner = err.message ?: "تعذر حفظ النص") }
            }
        }
    }

    fun deleteText(item: TextEntryItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { MediaImporter.deleteText(item) }
            scanIncoming()
        }
    }

    fun sendTextToComputer(text: String, baseName: String? = null) {
        viewModelScope.launch {
            val bridge = _uiState.value.bridgeUrl.trim()
            val message = withContext(Dispatchers.IO) {
                val outFile = MediaImporter.sendTextToComputerOutbox(getApplication(), text, baseName)
                if (bridge.isNotEmpty()) {
                    runCatching { MediaImporter.sendTextViaLanBridge(bridge, text) }
                        .getOrElse { "حُفظ في مجلد «إلى الكمبيوتر» (${outFile.name})، لكن تعذر الإرسال اللاسلكي: ${it.message}" }
                } else {
                    "تم تجهيز النص للكمبيوتر في مجلد «إلى الكمبيوتر» (${outFile.name})."
                }
            }
            _uiState.update { it.copy(statusBanner = message) }
            scanIncoming()
        }
    }

    fun updateBridgeUrl(url: String) {
        _uiState.update { it.copy(bridgeUrl = url) }
    }

    // ===== التنزيل الخاص بأسيد =====

    fun updateUrlsInput(value: String) {
        _uiState.update { it.copy(urlsInput = value) }
    }

    fun updateDownloadOptions(transform: (DownloadOptions) -> DownloadOptions) {
        _uiState.update { it.copy(downloadOptions = transform(it.downloadOptions)) }
    }

    fun startDownloads() {
        val urls = OsaidDownloaderEngine.parseUrls(_uiState.value.urlsInput)
        if (urls.isEmpty()) {
            _uiState.update { it.copy(statusBanner = "أدخل رابط فيديو أو صوت صالحاً (http/https).") }
            return
        }

        val options = _uiState.value.downloadOptions
        val newTasks = urls.map { url ->
            DownloadTaskItem(
                url = url,
                siteName = OsaidDownloaderEngine.detectSiteName(url),
                quality = options.quality
            )
        }

        _uiState.update {
            it.copy(
                downloadQueue = newTasks + it.downloadQueue,
                urlsInput = "",
                statusBanner = "تمت إضافة ${newTasks.size} رابط إلى قائمة التنزيل."
            )
        }

        if (!_uiState.value.isDownloading) {
            processDownloadQueue(options)
        }
    }

    private fun processDownloadQueue(options: DownloadOptions) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true) }

            while (true) {
                val next = _uiState.value.downloadQueue.firstOrNull { !it.isCompleted && !it.isFailed }
                    ?: break

                val processId = "dl_${UUID.randomUUID()}"
                _uiState.update { state ->
                    state.copy(
                        activeProcessId = processId,
                        downloadQueue = state.downloadQueue.map {
                            if (it.id == next.id) it.copy(statusText = "جارٍ التحضير…") else it
                        }
                    )
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        OsaidDownloaderEngine.download(
                            context = getApplication(),
                            processId = processId,
                            url = next.url,
                            options = options.copy(quality = next.quality)
                        ) { progress, eta, line ->
                            val cleanLine = line.trim().take(70)
                            val etaText = if (eta > 0) " · متبقي ${eta}ث" else ""
                            val msg = if (cleanLine.isNotEmpty()) {
                                "${(progress * 100).toInt()}%$etaText — $cleanLine"
                            } else {
                                "جارٍ التنزيل… ${(progress * 100).toInt()}%"
                            }
                            _uiState.update { state ->
                                state.copy(
                                    downloadQueue = state.downloadQueue.map {
                                        if (it.id == next.id) it.copy(progress = progress, statusText = msg) else it
                                    }
                                )
                            }
                        }
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        activeProcessId = null,
                        downloadQueue = state.downloadQueue.map { item ->
                            if (item.id != next.id) item
                            else result.fold(
                                onSuccess = { path ->
                                    item.copy(
                                        progress = 1f,
                                        isCompleted = true,
                                        outputPath = path,
                                        statusText = "تم التنزيل بنجاح: ${java.io.File(path).name}"
                                    )
                                },
                                onFailure = { err ->
                                    item.copy(
                                        isFailed = true,
                                        statusText = "فشل التنزيل: ${err.message ?: "خطأ غير معروف"}"
                                    )
                                }
                            )
                        }
                    )
                }
            }

            _uiState.update { it.copy(isDownloading = false, activeProcessId = null) }
        }
    }

    fun cancelActiveDownload() {
        val id = _uiState.value.activeProcessId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            OsaidDownloaderEngine.cancel(id)
        }
        _uiState.update { it.copy(statusBanner = "تم إرسال أمر إيقاف التنزيل الحالي.") }
    }

    fun updateDownloaderEngine() {
        if (_uiState.value.isUpdatingEngine) return
        _uiState.update { it.copy(isUpdatingEngine = true, statusBanner = "جارٍ تحديث yt-dlp…") }
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching { OsaidDownloaderEngine.updateEngine(getApplication()) }
                    .getOrElse { "تعذر تحديث المحرك: ${it.message}" }
            }
            _uiState.update { it.copy(isUpdatingEngine = false, statusBanner = msg) }
        }
    }
}
