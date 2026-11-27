import 'package:flutter/material.dart';
import '../services/usb_bridge_service.dart';
import '../theme/app_theme.dart';

class MediaBridgeScreen extends StatefulWidget {
  final UsbBridgeService bridge;
  final UsbBridgeScanResult? scanResult;
  final bool isScanning;
  final VoidCallback onRefresh;

  const MediaBridgeScreen({
    super.key,
    required this.bridge,
    required this.scanResult,
    required this.isScanning,
    required this.onRefresh,
  });

  @override
  State<MediaBridgeScreen> createState() => _MediaBridgeScreenState();
}

class _MediaBridgeScreenState extends State<MediaBridgeScreen> {
  bool _isImporting = false;
  double _progress = 0;
  String _currentName = '';
  String _summary = '';

  Future<void> _importAll() async {
    final items = widget.scanResult?.media ?? [];
    if (items.isEmpty || _isImporting) return;

    setState(() {
      _isImporting = true;
      _progress = 0;
      _summary = '';
    });

    final ok = await widget.bridge.requestPermissions();
    if (!ok) {
      setState(() {
        _isImporting = false;
        _summary = 'يرجى منح صلاحية الوصول إلى الصور والملفات أولاً';
      });
      return;
    }

    final (imported, skipped, failed) = await widget.bridge.importAllMedia(
      items,
      onProgress: (current, total, name) {
        if (!mounted) return;
        setState(() {
          _progress = current / total;
          _currentName = name;
        });
      },
    );

    if (!mounted) return;
    setState(() {
      _isImporting = false;
      _summary = 'تم استيراد $imported · تخطي المكرر $skipped · فشل $failed';
    });
    widget.onRefresh();
  }

  @override
  Widget build(BuildContext context) {
    final media = widget.scanResult?.media ?? [];
    final hasPerm = widget.scanResult?.hasStoragePermission ?? true;
    final imagesCount = media.where((e) => e.kind == BridgeMediaKind.image).length;
    final videosCount = media.where((e) => e.kind == BridgeMediaKind.video).length;

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        if (!hasPerm) ...[
          GlassCard(
            borderColor: AppTheme.accentAmber.withOpacity(0.55),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.warning_amber_rounded, color: AppTheme.accentAmber, size: 26),
                    SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'صلاحية الوصول للملفات مطلوبة لربط USB',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Text(
                  'حتى يتعرف برنامج Osaid File على ويندوز على الصور والنصوص في Download/OsaidFile، يرجى تفعيل "السماح بالوصول إلى جميع الملفات".',
                  style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
                ),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: () async {
                    await widget.bridge.openAllFilesPermission();
                  },
                  style: FilledButton.styleFrom(
                    backgroundColor: AppTheme.accentAmber,
                    foregroundColor: Colors.black,
                  ),
                  icon: const Icon(Icons.folder_open_rounded),
                  label: const Text('فتح إعدادات الصلاحية الآن', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
        ],
        GlassCard(
          borderColor: AppTheme.accentCyan.withOpacity(0.3),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppTheme.accentCyan.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(Icons.usb_rounded, color: AppTheme.accentCyan, size: 28),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'جسر USB للصور والفيديوهات',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.textPrimary),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'يستقبل الملفات المرسلة من برنامج Osaid File على ويندوز',
                          style: TextStyle(fontSize: 13, color: AppTheme.textSecondary.withOpacity(0.9)),
                        ),
                      ],
                    ),
                  ),
                  IconButton.filledTonal(
                    onPressed: widget.isScanning ? null : widget.onRefresh,
                    icon: widget.isScanning
                        ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.refresh_rounded),
                    tooltip: 'فحص الآن',
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.25),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  'مجلد الاستقبال: ${widget.scanResult?.inboxPath ?? "..."}',
                  style: const TextStyle(fontSize: 11, color: AppTheme.textSecondary),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: GlassCard(
                child: Column(
                  children: [
                    const Icon(Icons.photo_library_rounded, color: AppTheme.accentCyan, size: 28),
                    const SizedBox(height: 8),
                    Text('$imagesCount', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                    const Text('صور جاهزة', style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: GlassCard(
                child: Column(
                  children: [
                    const Icon(Icons.video_library_rounded, color: AppTheme.accentIndigo, size: 28),
                    const SizedBox(height: 8),
                    Text('$videosCount', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                    const Text('فيديوهات جاهزة', style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                  ],
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        if (_isImporting)
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('جاري الاستيراد: $_currentName', style: const TextStyle(fontSize: 13)),
                const SizedBox(height: 10),
                LinearProgressIndicator(value: _progress, borderRadius: BorderRadius.circular(8)),
              ],
            ),
          ),
        if (_summary.isNotEmpty) ...[
          const SizedBox(height: 12),
          GlassCard(
            borderColor: AppTheme.accentEmerald.withOpacity(0.4),
            child: Row(
              children: [
                const Icon(Icons.check_circle_rounded, color: AppTheme.accentEmerald),
                const SizedBox(width: 10),
                Expanded(child: Text(_summary, style: const TextStyle(color: AppTheme.textPrimary))),
              ],
            ),
          ),
        ],
        const SizedBox(height: 16),
        SizedBox(
          height: 52,
          child: FilledButton.icon(
            onPressed: (media.isEmpty || _isImporting) ? null : _importAll,
            style: FilledButton.styleFrom(
              backgroundColor: AppTheme.accentCyan,
              foregroundColor: Colors.black,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            ),
            icon: const Icon(Icons.download_done_rounded),
            label: Text(
              media.isEmpty ? 'لا توجد وسائط جديدة للاستيراد' : 'استيراد الكل إلى المعرض (${media.length})',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
            ),
          ),
        ),
        const SizedBox(height: 20),
        if (media.isNotEmpty) ...[
          const Text('قائمة الملفات الواردة', style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          ...media.map(
            (item) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: GlassCard(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                child: Row(
                  children: [
                    Icon(
                      item.kind == BridgeMediaKind.image ? Icons.image_rounded : Icons.videocam_rounded,
                      color: item.kind == BridgeMediaKind.image ? AppTheme.accentCyan : AppTheme.accentIndigo,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(item.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                          Text(item.sizeText, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ],
    );
  }
}
