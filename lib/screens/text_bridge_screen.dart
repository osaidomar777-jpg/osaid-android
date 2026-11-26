import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import '../services/usb_bridge_service.dart';
import '../theme/app_theme.dart';

class TextBridgeScreen extends StatefulWidget {
  final UsbBridgeService bridge;
  final UsbBridgeScanResult? scanResult;
  final VoidCallback onRefresh;

  const TextBridgeScreen({
    super.key,
    required this.bridge,
    required this.scanResult,
    required this.onRefresh,
  });

  @override
  State<TextBridgeScreen> createState() => _TextBridgeScreenState();
}

class _TextBridgeScreenState extends State<TextBridgeScreen> {
  final _titleController = TextEditingController();
  final _bodyController = TextEditingController();
  bool _sending = false;

  Future<void> _sendToPc() async {
    final text = _bodyController.text.trim();
    if (text.isEmpty || _sending) return;
    setState(() => _sending = true);
    try {
      await widget.bridge.sendTextToComputer(_titleController.text, text);
      _titleController.clear();
      _bodyController.clear();
      widget.onRefresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تم حفظ النص في مجلد "إلى الكمبيوتر" بنجاح')),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _openTextFile(BridgeTextFile tf) async {
    final content = await tf.file.readAsString();
    if (!mounted) return;
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.bgNavy,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(tf.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                ),
                IconButton(
                  icon: const Icon(Icons.copy_rounded),
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: content));
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم النسخ')));
                  },
                ),
                IconButton(
                  icon: const Icon(Icons.share_rounded),
                  onPressed: () => Share.share(content),
                ),
              ],
            ),
            const Divider(),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 360),
              child: SingleChildScrollView(
                child: SelectableText(content, style: const TextStyle(fontSize: 14, height: 1.5)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final texts = widget.scanResult?.texts ?? [];
    final outboxCount = widget.scanResult?.outboxCount ?? 0;

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        GlassCard(
          borderColor: AppTheme.accentIndigo.withOpacity(0.3),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(Icons.send_to_mobile_rounded, color: AppTheme.accentIndigo, size: 26),
                  SizedBox(width: 10),
                  Text('إرسال نص إلى الكمبيوتر (USB)', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _titleController,
                decoration: InputDecoration(
                  hintText: 'عنوان الملف (اختياري)',
                  filled: true,
                  fillColor: Colors.black.withOpacity(0.25),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _bodyController,
                maxLines: 4,
                decoration: InputDecoration(
                  hintText: 'اكتب أو الصق النص هنا لإرساله إلى برنامج ويندوز...',
                  filled: true,
                  fillColor: Colors.black.withOpacity(0.25),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: _sending ? null : _sendToPc,
                      style: FilledButton.styleFrom(
                        backgroundColor: AppTheme.accentIndigo,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      icon: const Icon(Icons.send_rounded),
                      label: Text(_sending ? 'جاري الإرسال...' : 'إرسال إلى الكمبيوتر ($outboxCount)'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        Text('النصوص الواردة من الكمبيوتر (${texts.length})',
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        const SizedBox(height: 10),
        if (texts.isEmpty)
          const GlassCard(
            child: Center(
              child: Padding(
                padding: EdgeInsets.all(20),
                child: Text('لا توجد ملفات نصية واردة حالياً', style: TextStyle(color: AppTheme.textSecondary)),
              ),
            ),
          )
        else
          ...texts.map(
            (tf) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: InkWell(
                onTap: () => _openTextFile(tf),
                borderRadius: BorderRadius.circular(20),
                child: GlassCard(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  child: Row(
                    children: [
                      const Icon(Icons.description_rounded, color: AppTheme.accentCyan),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(tf.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                      ),
                      const Icon(Icons.chevron_right_rounded, color: AppTheme.textSecondary),
                    ],
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
