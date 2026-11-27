import 'dart:io';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';
import 'package:gal/gal.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum BridgeMediaKind { image, video }

class BridgeMediaItem {
  final File file;
  final BridgeMediaKind kind;
  final int size;

  BridgeMediaItem({required this.file, required this.kind, required this.size});

  String get name => file.uri.pathSegments.last;

  String get sizeText {
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}

class BridgeTextFile {
  final File file;
  final int size;
  final DateTime modified;

  BridgeTextFile({required this.file, required this.size, required this.modified});

  String get name => file.uri.pathSegments.last;
}

class UsbBridgeScanResult {
  final List<BridgeMediaItem> media;
  final List<BridgeTextFile> texts;
  final int outboxCount;
  final String inboxPath;
  final bool hasStoragePermission;

  UsbBridgeScanResult({
    required this.media,
    required this.texts,
    required this.outboxCount,
    required this.inboxPath,
    required this.hasStoragePermission,
  });
}

class UsbBridgeService {
  static const _bridgeChannel = MethodChannel('com.osaid.file/bridge');
  static const _fingerprintKey = 'osaid.imported.fingerprints';
  static const _imageExts = {
    'heic', 'heif', 'jpg', 'jpeg', 'png', 'gif', 'tif', 'tiff', 'webp', 'bmp'
  };
  static const _videoExts = {'mov', 'mp4', 'm4v', '3gp', 'avi', 'mkv'};
  static const _textExts = {
    'txt', 'md', 'csv', 'json', 'log', 'xml', 'yml', 'yaml', 'ini', 'html', 'htm', 'rtf'
  };

  Future<bool> hasAllFilesPermission() async {
    if (!Platform.isAndroid) return true;
    try {
      return await _bridgeChannel.invokeMethod<bool>('hasAllFilesPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<void> openAllFilesPermission() async {
    if (!Platform.isAndroid) return;
    try {
      await _bridgeChannel.invokeMethod('openAllFilesPermission');
    } catch (_) {}
  }

  Future<void> scanMediaFile(String path) async {
    if (!Platform.isAndroid) return;
    try {
      await _bridgeChannel.invokeMethod('scanFile', {'path': path});
    } catch (_) {}
  }

  /// المجلد المشترك الذي يقرأه برنامج Osaid File على ويندوز عبر USB MTP:
  /// Android: /storage/emulated/0/Download/OsaidFile
  /// iOS/iPadOS: Documents
  Future<Directory> getBridgeRootDirectory() async {
    if (Platform.isIOS) {
      return await getApplicationDocumentsDirectory();
    }
    const sharedPath = '/storage/emulated/0/Download/OsaidFile';
    final sharedDir = Directory(sharedPath);
    try {
      if (!await sharedDir.exists()) {
        await sharedDir.create(recursive: true);
      }
      return sharedDir;
    } catch (_) {
      final ext = await getExternalStorageDirectory();
      return ext ?? await getApplicationDocumentsDirectory();
    }
  }

  Future<Directory> getOutboxDirectory() async {
    final root = await getBridgeRootDirectory();
    final outbox = Directory('${root.path}/إلى الكمبيوتر');
    if (!await outbox.exists()) {
      await outbox.create(recursive: true);
    }
    return outbox;
  }

  Future<bool> requestPermissions() async {
    if (Platform.isAndroid) {
      await [
        Permission.photos,
        Permission.videos,
        Permission.storage,
      ].request();
      final hasAll = await hasAllFilesPermission();
      if (!hasAll) {
        await openAllFilesPermission();
        return false;
      }
      return true;
    } else if (Platform.isIOS) {
      return await Gal.requestAccess(toAlbum: true);
    }
    return true;
  }

  Future<UsbBridgeScanResult> scanInbox() async {
    final hasPerm = await hasAllFilesPermission();
    final root = await getBridgeRootDirectory();
    await getOutboxDirectory();

    final media = <BridgeMediaItem>[];
    final texts = <BridgeTextFile>[];
    int outboxCount = 0;

    if (await root.exists()) {
      try {
        await for (final entity in root.list(recursive: true, followLinks: false)) {
          if (entity is! File) continue;
          final path = entity.path;
          if (path.contains('إلى الكمبيوتر')) {
            outboxCount++;
            continue;
          }
          if (path.contains('/Downloads/')) {
            continue;
          }
          final ext = path.split('.').last.toLowerCase();
          final stat = await entity.stat();
          if (_imageExts.contains(ext)) {
            media.add(BridgeMediaItem(file: entity, kind: BridgeMediaKind.image, size: stat.size));
          } else if (_videoExts.contains(ext)) {
            media.add(BridgeMediaItem(file: entity, kind: BridgeMediaKind.video, size: stat.size));
          } else if (_textExts.contains(ext)) {
            texts.add(BridgeTextFile(file: entity, size: stat.size, modified: stat.modified));
          }
        }
      } catch (_) {}
    }

    texts.sort((a, b) => b.modified.compareTo(a.modified));
    return UsbBridgeScanResult(
      media: media,
      texts: texts,
      outboxCount: outboxCount,
      inboxPath: root.path,
      hasStoragePermission: hasPerm,
    );
  }

  Future<String> _fingerprint(File file, int size) async {
    final raf = await file.open();
    try {
      const chunk = 262144;
      final head = await raf.read(chunk);
      Uint8List tail = Uint8List(0);
      if (size > chunk * 2) {
        await raf.setPosition(size - chunk);
        tail = await raf.read(chunk);
      }
      final bytes = BytesBuilder()
        ..add(utf8SizeBytes(size))
        ..add(head)
        ..add(tail);
      return sha256.convert(bytes.toBytes()).toString();
    } finally {
      await raf.close();
    }
  }

  List<int> utf8SizeBytes(int size) {
    final bd = ByteData(8)..setInt64(0, size, Endian.little);
    return bd.buffer.asUint8List();
  }

  Future<(int imported, int skipped, int failed)> importAllMedia(
    List<BridgeMediaItem> items, {
    void Function(int current, int total, String name)? onProgress,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final seen = (prefs.getStringList(_fingerprintKey) ?? []).toSet();

    int imported = 0;
    int skipped = 0;
    int failed = 0;

    for (var i = 0; i < items.length; i++) {
      final item = items[i];
      onProgress?.call(i + 1, items.length, item.name);

      try {
        final fp = await _fingerprint(item.file, item.size);
        if (seen.contains(fp)) {
          skipped++;
          continue;
        }
        if (item.kind == BridgeMediaKind.image) {
          await Gal.putImage(item.file.path, album: 'Osaid File');
        } else {
          await Gal.putVideo(item.file.path, album: 'Osaid File');
        }
        seen.add(fp);
        imported++;
      } catch (_) {
        failed++;
      }
    }

    await prefs.setStringList(_fingerprintKey, seen.toList());
    return (imported, skipped, failed);
  }

  /// إرسال نص إلى مجلد "إلى الكمبيوتر" وتحديث MediaScanner فوراً ليراه ويندوز عبر USB MTP
  Future<File> sendTextToComputer(String title, String content) async {
    final outbox = await getOutboxDirectory();
    final safeName = (title.trim().isEmpty ? 'نص_${DateTime.now().millisecondsSinceEpoch}' : title.trim())
        .replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
    final fileName = safeName.endsWith('.txt') ? safeName : '$safeName.txt';
    final file = File('${outbox.path}/$fileName');
    await file.writeAsString(content, flush: true);
    await scanMediaFile(file.path);
    return file;
  }

  /// إرسال أي ملف (صورة/فيديو/مستند) من الجوال إلى مجلد "إلى الكمبيوتر"
  Future<File> sendFileToComputer(File source) async {
    final outbox = await getOutboxDirectory();
    final name = source.uri.pathSegments.last;
    final dest = File('${outbox.path}/$name');
    final copied = await source.copy(dest.path);
    await scanMediaFile(copied.path);
    return copied;
  }
}
