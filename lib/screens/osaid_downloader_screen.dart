import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import '../services/osaid_downloader_service.dart';
import '../theme/app_theme.dart';

class OsaidDownloaderScreen extends StatefulWidget {
  const OsaidDownloaderScreen({super.key});

  @override
  State<OsaidDownloaderScreen> createState() => _OsaidDownloaderScreenState();
}

class _OsaidDownloaderScreenState extends State<OsaidDownloaderScreen> {
  final _inputController = TextEditingController();
  final _downloader = OsaidDownloaderService();
  final List<OsaidDownloadTask> _tasks = [];
  List<OsaidSearchResult> _searchResults = [];

  OsaidQuality _selectedQuality = OsaidQuality.bestMp4;
  String _activeTab = 'all';
  bool _isSearching = false;
  bool _isUpdatingEngine = false;
  String _engineStatus = 'جاري فحص المحرك...';
  bool _engineReady = false;
  StreamSubscription? _sub;

  @override
  void initState() {
    super.initState();
    _loadEngineInfo();
    _sub = _downloader.progressStream.listen((event) {
      final id = event['id']?.toString();
      if (id == null) return;
      final idx = _tasks.indexWhere((t) => t.id == id);
      if (idx == -1) return;
      final p = (event['progress'] as num?)?.toDouble() ?? 0.0;
      setState(() {
        if (p >= 0) _tasks[idx].progress = (p / 100.0).clamp(0.0, 1.0);
        _tasks[idx].statusText = event['line']?.toString() ?? 'جاري التحميل...';
      });
    });
  }

  Future<void> _loadEngineInfo() async {
    final info = await _downloader.getEngineInfo();
    if (!mounted) return;
    setState(() {
      _engineReady = info['ready'] == true;
      _engineStatus = _engineReady
          ? 'المحرك جاهز (yt-dlp ${info['version']})'
          : 'حالة المحرك: ${info['version']}';
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    _inputController.dispose();
    super.dispose();
  }

  Future<void> _pasteInput() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    if (data?.text != null && data!.text!.trim().isNotEmpty) {
      setState(() => _inputController.text = data.text!.trim());
    }
  }

  Future<void> _updateEngine() async {
    if (_isUpdatingEngine) return;
    setState(() => _isUpdatingEngine = true);
    try {
      final res = await _downloader.updateEngine();
      await _loadEngineInfo();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('حالة تحديث محرك التحميل: $res')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر التحديث: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _isUpdatingEngine = false);
    }
  }

  Future<void> _handleAction() async {
    final text = _inputController.text.trim();
    if (text.isEmpty) return;

    if (_downloader.isUrl(text)) {
      await _enqueueDownload(url: text, quality: _selectedQuality);
      _inputController.clear();
    } else {
      setState(() {
        _isSearching = true;
        _searchResults = [];
      });
      try {
        final results = await _downloader.search(text);
        if (mounted) setState(() => _searchResults = results);
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('تعذر البحث: $e')),
          );
        }
      } finally {
        if (mounted) setState(() => _isSearching = false);
      }
    }
  }

  Future<void> _enqueueDownload({
    required String url,
    required OsaidQuality quality,
    String? knownTitle,
  }) async {
    final id = DateTime.now().millisecondsSinceEpoch.toString();
    final task = OsaidDownloadTask(
      id: id,
      url: url,
      title: knownTitle ?? 'جاري التحليل...',
      quality: quality,
      status: DownloadStatus.downloading,
      statusText: 'جاري البدء...',
    );

    setState(() {
      _tasks.insert(0, task);
    });

    if (knownTitle == null) {
      final info = await _downloader.analyzeUrl(url);
      if (mounted) {
        setState(() {
          task.title = info['title']?.toString() ?? url;
          task.site = info['site']?.toString() ?? '';
          task.statusText = 'جاري التحميل...';
        });
      }
    }

    try {
      final path = await _downloader.startDownload(
        id: id,
        url: url,
        quality: quality,
        onDirectProgress: (pct, status) {
          if (!mounted) return;
          setState(() {
            task.progress = pct.clamp(0.0, 1.0);
            task.statusText = status;
          });
        },
      );
      if (!mounted) return;
      setState(() {
        task.status = DownloadStatus.completed;
        task.progress = 1.0;
        task.filePath = path;
        task.statusText = 'مكتمل — حُفظ في ${path ?? "المعرض"}';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        task.status = DownloadStatus.failed;
        task.statusText = 'فشل: ${e.toString().replaceAll("PlatformException(", "").replaceAll(")", "")}';
      });
    }
  }

  List<OsaidDownloadTask> get _filteredTasks {
    switch (_activeTab) {
      case 'active':
        return _tasks.where((t) => t.status == DownloadStatus.downloading || t.status == DownloadStatus.queued).toList();
      case 'done':
        return _tasks.where((t) => t.status == DownloadStatus.completed).toList();
      case 'failed':
        return _tasks.where((t) => t.status == DownloadStatus.failed).toList();
      default:
        return _tasks;
    }
  }

  @override
  Widget build(BuildContext context) {
    final list = _filteredTasks;
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        GlassCard(
          borderColor: AppTheme.accentEmerald.withOpacity(0.35),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.cloud_download_rounded, color: AppTheme.accentEmerald, size: 28),
                  const SizedBox(width: 10),
                  const Expanded(
                    child: Text('محمّل أسيد — Osaid Downloader',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  ),
                  IconButton.filledTonal(
                    onPressed: _isUpdatingEngine ? null : _updateEngine,
                    icon: _isUpdatingEngine
                        ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.system_update_alt_rounded, size: 20),
                    tooltip: 'تحديث محرك التحميل من GitHub',
                  ),
                ],
              ),
              const SizedBox(height: 6),
              const Text(
                'ابحث عن أي أغنية أو فيديو بالاسم (مثل طرب) أو الصق رابطاً من يوتيوب وتيك توك وإنستغرام وغيرها',
                style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(
                    _engineReady ? Icons.check_circle_rounded : Icons.info_outline_rounded,
                    size: 16,
                    color: _engineReady ? AppTheme.accentEmerald : AppTheme.accentAmber,
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      _engineStatus,
                      style: TextStyle(
                        fontSize: 11,
                        color: _engineReady ? AppTheme.accentEmerald : AppTheme.accentAmber,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _inputController,
                      onSubmitted: (_) => _handleAction(),
                      decoration: InputDecoration(
                        hintText: 'اكتب اسم أغنية/فيديو أو الصق رابطاً...',
                        filled: true,
                        fillColor: Colors.black.withOpacity(0.28),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filledTonal(
                    onPressed: _pasteInput,
                    icon: const Icon(Icons.content_paste_rounded),
                    tooltip: 'لصق',
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<OsaidQuality>(
                      value: _selectedQuality,
                      dropdownColor: AppTheme.bgNavy,
                      decoration: InputDecoration(
                        filled: true,
                        fillColor: Colors.black.withOpacity(0.28),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: BorderSide.none,
                        ),
                      ),
                      items: OsaidQuality.values
                          .map((q) => DropdownMenuItem(value: q, child: Text(q.label)))
                          .toList(),
                      onChanged: (v) {
                        if (v != null) setState(() => _selectedQuality = v);
                      },
                    ),
                  ),
                  const SizedBox(width: 10),
                  SizedBox(
                    height: 48,
                    child: FilledButton.icon(
                      onPressed: _isSearching ? null : _handleAction,
                      style: FilledButton.styleFrom(
                        backgroundColor: AppTheme.accentEmerald,
                        foregroundColor: Colors.black,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      icon: _isSearching
                          ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                          : const Icon(Icons.search_rounded),
                      label: const Text('بحث / تحميل', style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        if (_searchResults.isNotEmpty) ...[
          const SizedBox(height: 18),
          Row(
            children: [
              const Expanded(
                child: Text('نتائج البحث', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              ),
              TextButton(
                onPressed: () => setState(() => _searchResults = []),
                child: const Text('إخفاء'),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ..._searchResults.map(
            (r) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: GlassCard(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(10),
                      child: Image.network(
                        r.thumbnailUrl,
                        width: 92,
                        height: 62,
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => Container(
                          width: 92,
                          height: 62,
                          color: Colors.black26,
                          child: const Icon(Icons.music_note_rounded),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(r.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                          const SizedBox(height: 4),
                          Text('${r.author} • ${r.duration}', style: const TextStyle(fontSize: 11, color: AppTheme.textSecondary)),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              FilledButton.tonalIcon(
                                onPressed: () => _enqueueDownload(
                                  url: r.url,
                                  quality: OsaidQuality.mp3,
                                  knownTitle: r.title,
                                ),
                                icon: const Icon(Icons.music_note_rounded, size: 16),
                                label: const Text('صوت', style: TextStyle(fontSize: 12)),
                              ),
                              const SizedBox(width: 8),
                              FilledButton.tonalIcon(
                                onPressed: () => _enqueueDownload(
                                  url: r.url,
                                  quality: _selectedQuality.isAudioOnly ? OsaidQuality.bestMp4 : _selectedQuality,
                                  knownTitle: r.title,
                                ),
                                icon: const Icon(Icons.videocam_rounded, size: 16),
                                label: const Text('فيديو', style: TextStyle(fontSize: 12)),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
        const SizedBox(height: 16),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              _tabChip('all', 'الكل (${_tasks.length})'),
              const SizedBox(width: 8),
              _tabChip('active', 'جاري التحميل'),
              const SizedBox(width: 8),
              _tabChip('done', 'مكتمل'),
              const SizedBox(width: 8),
              _tabChip('failed', 'فشل'),
            ],
          ),
        ),
        const SizedBox(height: 14),
        if (list.isEmpty)
          const GlassCard(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: Center(
                child: Text('لا توجد تنزيلات في هذه القائمة', style: TextStyle(color: AppTheme.textSecondary)),
              ),
            ),
          )
        else
          ...list.map(
            (t) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: GlassCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(t.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontWeight: FontWeight.bold)),
                        ),
                        if (t.status == DownloadStatus.completed && t.filePath != null)
                          IconButton(
                            icon: const Icon(Icons.share_rounded, color: AppTheme.accentCyan),
                            onPressed: () => Share.shareXFiles([XFile(t.filePath!)]),
                            tooltip: 'مشاركة / فتح',
                          ),
                        if (t.status == DownloadStatus.downloading)
                          IconButton(
                            icon: const Icon(Icons.close_rounded, color: AppTheme.accentRose),
                            onPressed: () {
                              _downloader.cancelDownload(t.id);
                              setState(() {
                                t.status = DownloadStatus.cancelled;
                                t.statusText = 'تم الإلغاء';
                              });
                            },
                          ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text('${t.quality.label} • ${t.statusText}',
                        style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                    if (t.status == DownloadStatus.downloading) ...[
                      const SizedBox(height: 8),
                      LinearProgressIndicator(value: t.progress > 0 ? t.progress : null),
                    ],
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _tabChip(String key, String label) {
    final selected = _activeTab == key;
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => setState(() => _activeTab = key),
      selectedColor: AppTheme.accentEmerald.withOpacity(0.25),
    );
  }
}
