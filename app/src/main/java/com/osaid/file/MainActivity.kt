package com.osaid.file

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val BgDark = Color(0xFF080D17)
private val CardDark = Color(0xFF101A2B)
private val CardSecondary = Color(0xFF142138)
private val AccentCyan = Color(0xFF22D3EE)
private val AccentBlue = Color(0xFF38BDF8)
private val AccentViolet = Color(0xFFA78BFA)
private val SuccessGreen = Color(0xFF34D399)
private val DangerRed = Color(0xFFF87171)
private val TextPrimary = Color(0xFFE8EEF8)
private val TextSecondary = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = AccentBlue,
                        secondary = AccentViolet,
                        background = BgDark,
                        surface = CardDark,
                        onPrimary = Color.Black,
                        onBackground = TextPrimary,
                        onSurface = TextPrimary
                    )
                ) {
                    OsaidAndroidScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                viewModel.setSharedUrl(shared)
            }
        }
    }
}

@Composable
fun OsaidAndroidScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        vm.setPermissionGranted(granted)
        vm.scanIncoming()
    }

    val pickFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        vm.addPickedFiles(uris)
    }

    LaunchedEffect(Unit) {
        val granted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        vm.setPermissionGranted(granted)
        if (!granted) {
            permLauncher.launch(requiredPermissions)
        } else {
            vm.scanIncoming()
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070D17))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(AccentCyan, AccentViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Osaid File", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "التنزيل الخاص بأسيد + جسر الوسائط والنصوص",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = { vm.scanIncoming() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = AccentBlue)
                    }
                }

                if (state.statusBanner.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = AccentBlue.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                state.statusBanner,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.clearBanner() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A111E)) {
                NavigationBarItem(
                    selected = state.activeTab == AppTab.DOWNLOADER,
                    onClick = { vm.setTab(AppTab.DOWNLOADER) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    label = { Text("التنزيل", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = state.activeTab == AppTab.MEDIA,
                    onClick = { vm.setTab(AppTab.MEDIA) },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    label = { Text("الوسائط (${state.mediaItems.size})", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = state.activeTab == AppTab.TEXTS,
                    onClick = { vm.setTab(AppTab.TEXTS) },
                    icon = { Icon(Icons.Default.TextSnippet, contentDescription = null) },
                    label = { Text("النصوص (${state.textFiles.size})", fontSize = 11.sp) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.activeTab) {
                AppTab.DOWNLOADER -> DownloaderTab(vm, state)
                AppTab.MEDIA -> MediaTab(
                    vm = vm,
                    state = state,
                    onRequestPermission = { permLauncher.launch(requiredPermissions) },
                    onPickFiles = { pickFilesLauncher.launch(arrayOf("image/*", "video/*", "text/*")) }
                )
                AppTab.TEXTS -> TextsTab(vm, state)
            }
        }
    }
}

@Composable
private fun DownloaderTab(vm: MainViewModel, state: MainUiState) {
    val context = LocalContext.current
    val options = state.downloadOptions

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "التنزيل الخاص بأسيد (1800+ موقع)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { vm.updateDownloaderEngine() },
                            enabled = !state.isUpdatingEngine
                        ) {
                            Text(if (state.isUpdatingEngine) "جارٍ التحديث…" else "تحديث المحرك", fontSize = 11.5.sp)
                        }
                    }

                    OutlinedTextField(
                        value = state.urlsInput,
                        onValueChange = { vm.updateUrlsInput(it) },
                        label = { Text("الصق رابطاً أو عدة روابط (كل رابط في سطر)") },
                        placeholder = { Text("https://youtube.com/... أو TikTok / Instagram / X") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (clip.isNotBlank()) vm.setSharedUrl(clip)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("لصق من الحافظة", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { vm.startDownloads() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("بدء التنزيل", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }

                    Text("الجودة والصيغة", fontSize = 12.sp, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DownloadQuality.entries) { q ->
                            FilterChip(
                                selected = options.quality == q,
                                onClick = { vm.updateDownloadOptions { it.copy(quality = q) } },
                                label = { Text(q.label, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تضمين الغلاف والبيانات", fontSize = 12.5.sp, color = TextPrimary)
                        Switch(
                            checked = options.embedMetadataAndThumb,
                            onCheckedChange = { checked ->
                                vm.updateDownloadOptions { it.copy(embedMetadataAndThumb = checked) }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ترجمة عربية/إنجليزية", fontSize = 12.5.sp, color = TextPrimary)
                        Switch(
                            checked = options.downloadSubtitles,
                            onCheckedChange = { checked ->
                                vm.updateDownloadOptions { it.copy(downloadSubtitles = checked) }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تخطي الإعلانات (SponsorBlock)", fontSize = 12.5.sp, color = TextPrimary)
                        Switch(
                            checked = options.sponsorBlockSkip,
                            onCheckedChange = { checked ->
                                vm.updateDownloadOptions { it.copy(sponsorBlockSkip = checked) }
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = options.clipStart,
                            onValueChange = { v -> vm.updateDownloadOptions { it.copy(clipStart = v) } },
                            label = { Text("قص من (0:15)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = options.clipEnd,
                            onValueChange = { v -> vm.updateDownloadOptions { it.copy(clipEnd = v) } },
                            label = { Text("إلى (1:00)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (state.isDownloading) {
            item {
                OutlinedButton(
                    onClick = { vm.cancelActiveDownload() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("إيقاف التنزيل الحالي")
                }
            }
        }

        items(state.downloadQueue, key = { it.id }) { task ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSecondary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${task.siteName} · ${task.quality.label}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = when {
                                task.isCompleted -> Icons.Default.CheckCircle
                                task.isFailed -> Icons.Default.Error
                                else -> Icons.Default.Downloading
                            },
                            contentDescription = null,
                            tint = when {
                                task.isCompleted -> SuccessGreen
                                task.isFailed -> DangerRed
                                else -> AccentBlue
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(task.url, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    if (!task.isCompleted && !task.isFailed) {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = AccentBlue
                        )
                    }

                    Text(
                        task.statusText,
                        fontSize = 11.5.sp,
                        color = if (task.isFailed) DangerRed else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaTab(
    vm: MainViewModel,
    state: MainUiState,
    onRequestPermission: () -> Unit,
    onPickFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الملفات الواردة", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AssistChip(
                        onClick = onRequestPermission,
                        label = {
                            Text(
                                if (state.hasMediaPermission) "الإذن مفعّل" else "تفعيل الإذن",
                                fontSize = 11.sp,
                                color = if (state.hasMediaPermission) SuccessGreen else Color(0xFFFBBF24)
                            )
                        }
                    )
                }

                Text(
                    "${state.mediaItems.size}",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    if (state.mediaItems.isEmpty()) "بانتظار ملفات جديدة (${state.totalMediaSizeText})"
                    else "ملف جاهز للاستيراد إلى المعرض (${state.totalMediaSizeText})",
                    fontSize = 12.5.sp,
                    color = TextSecondary
                )
                Text(
                    "المجلد: Documents/Osaid File",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("اختيار ملفات", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { vm.importAllMedia() },
                        enabled = state.mediaItems.isNotEmpty() && !state.isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isImporting) "جارٍ الاستيراد…" else "استيراد إلى المعرض", fontSize = 12.sp)
                    }
                }

                if (state.isImporting) {
                    LinearProgressIndicator(
                        progress = { state.importProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = AccentCyan
                    )
                    Text(state.importCurrentName, fontSize = 11.5.sp, color = TextSecondary)
                }

                if (state.importSummary.isNotEmpty()) {
                    Text(state.importSummary, fontSize = 12.5.sp, color = SuccessGreen, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (state.logs.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("سجل العمليات", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearLogs() }) {
                            Text("مسح السجل", color = DangerRed, fontSize = 12.sp)
                        }
                    }
                    state.logs.take(12).forEach { entry ->
                        Text(
                            entry.text,
                            fontSize = 12.sp,
                            color = if (entry.ok) TextPrimary else DangerRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextsTab(vm: MainViewModel, state: MainUiState) {
    val context = LocalContext.current
    var showNewDialog by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var openedItem by remember { mutableStateOf<TextEntryItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("نقل النصوص والكمبيوتر", fontWeight = FontWeight.Bold, color = AccentBlue, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showNewDialog = true }) {
                        Text("+ نص جديد")
                    }
                }

                OutlinedTextField(
                    value = state.bridgeUrl,
                    onValueChange = { vm.updateBridgeUrl(it) },
                    label = { Text("رابط جسر Wi-Fi (اختياري للإرسال اللاسلكي)") },
                    placeholder = { Text("http://192.168.1.5:5050") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    if (state.outboxCount == 0) "لإرسال نص إلى الكمبيوتر: افتح أي نص ← «إرسال للكمبيوتر»."
                    else "${state.outboxCount} ملف جاهز للكمبيوتر في مجلد «إلى الكمبيوتر».",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                if (state.textFiles.isEmpty()) {
                    Text(
                        "لا توجد ملفات نصية بعد. انقل txt / md / json من الكمبيوتر أو أضف نصاً جديداً.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                } else {
                    state.textFiles.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardSecondary)
                                .clickable { openedItem = file }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = AccentBlue)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(file.sizeText, fontSize = 11.sp, color = TextSecondary)
                            }
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("نص جديد") },
            text = {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    minLines = 4,
                    placeholder = { Text("اكتب النص هنا…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newText.isNotBlank()) {
                        vm.saveNewText(newText)
                        newText = ""
                        showNewDialog = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text("إلغاء") }
            }
        )
    }

    openedItem?.let { item ->
        val content = remember(item) { MediaImporter.readText(item) }
        AlertDialog(
            onDismissRequest = { openedItem = null },
            title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(content, fontSize = 13.5.sp, color = TextPrimary)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(item.name, content))
                    }) { Text("نسخ") }
                    Button(onClick = {
                        vm.sendTextToComputer(content, item.name)
                        openedItem = null
                    }) { Text("إرسال للكمبيوتر") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteText(item)
                    openedItem = null
                }) { Text("حذف", color = DangerRed) }
            }
        )
    }
}
