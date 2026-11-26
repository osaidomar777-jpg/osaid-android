import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/osaid_downloader_service.dart';
import '../theme/app_theme.dart';

class OsaidDownloaderScreen extends StatefulWidget {
  const OsaidDownloaderScreen({super.key});

  @override
  State<OsaidDownloaderScreen> createState() => _OsaidDownloaderScreenState();
}

class _OsaidDownloaderScreenState extends State<OsaidDownloaderScreen> {
  final _urlController = TextEditingController();
  final _downloader = OsaidDownloaderService();
  final List<OsaidDownloadTask> _tasks = [];

  OsaidQuality _selectedQuality = OsaidQuality.bestMp4;
  String _activeTab = 'all';
  bool _analyzing = false;
  StreamSubscription? _sub;

  @override
  void initState() {
    super.initState();
    _sub = _downloader.progressStream.listen((event) {
      final id = event['id']?.toString();
      if (id == null) return;
      final idx = _tasks.indexWhere((t) => t.id == id);
      if (idx == -1) return;
      setState(() {
        _tasks[idx].progress = ((event['progress'] as num?)?.toDouble() ?? 0.0) / 100.0;
        _tasks[idx].statusText = event['line']?.toString() ?? 'جاري التحميل...';
      });
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _pasteUrl() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    if (data?.text != null && data!.text!.trim().isNotEmpty) {
      setState(() => _urlController.text = data.text!.trim());
    }
  }

  Future<void> _startDownload() async {
    final url = _urlController.text.trim();
    if (url.isEmpty) return;

    final id = DateTime.now().millisecondsSinceEpoch.toString();
    final task = OsaidDownloadTask(
      id: id,
      url: url,
      quality: _selectedQuality,
      status: DownloadStatus.downloading,
      statusText: 'جاري التحليل...',
    );

    setState(() {
      _tasks.insert(0, task);
      _urlController.clear();
      _analyzing = true;
    });

    final info = await _downloader.analyzeUrl(url);
    if (mounted) {
      setState(() {
        _analyzing = false;
        task.title = info['title']?.toString() ?? url;
        task.site = info['site']?.toString() ?? '';
        task.statusText = 'جاري التحميل...';
      });
    }

    try {
      final path = await _downloader.startDownload(
        id: id,
        url: url,
        quality: task.quality,
      );
      if (!mounted) return;
      setState(() {
        task.status = DownloadStatus.completed;
        task.progress = 1.0;
        task.filePath = path;
        task.statusText = 'مكتمل — تم الحفظ';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        task.status = DownloadStatus.failed;
        task.statusText = 'فشل التحميل';
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
          borderColor: AppTheme.accentEmerald.withOpacity(0.3),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(Icons.cloud_download_rounded, color: AppTheme.accentEmerald, size: 28),
                  SizedBox(width: 10),
                  Text('محمّل أسيد — Osaid Downloader',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                ],
              ),
              const SizedBox(height: 6),
              const Text(
                'تحميل الفيديوهات والصور والصوتيات بنفس جودات برنامج الويندوز',
                style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _urlController,
                      decoration: InputDecoration(
                        hintText: 'الصق رابط الفيديو أو الصورة هنا...',
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
                    onPressed: _pasteUrl,
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
                      onPressed: _analyzing ? null : _startDownload,
                      style: FilledButton.styleFrom(
                        backgroundColor: AppTheme.accentEmerald,
                        foregroundColor: Colors.black,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      icon: const Icon(Icons.download_rounded),
                      label: const Text('تحميل', style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
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
