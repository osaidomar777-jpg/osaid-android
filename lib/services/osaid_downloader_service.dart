import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:youtube_explode_dart/youtube_explode_dart.dart';

/// نفس الجودات الموجودة في برنامج الويندوز (Osaid Downloader)
enum OsaidQuality {
  bestMp4('أفضل جودة · MP4', 'best_mp4'),
  q2160('2160p · 4K', '2160'),
  q1440('1440p · 2K', '1440'),
  q1080('1080p · Full HD', '1080'),
  q720('720p · HD', '720'),
  q480('480p', '480'),
  mp3('صوت · MP3', 'mp3'),
  m4a('صوت · M4A', 'm4a'),
  flac('صوت · FLAC', 'flac'),
  wav('صوت · WAV', 'wav');

  final String label;
  final String key;
  const OsaidQuality(this.label, this.key);

  bool get isAudioOnly => this == mp3 || this == m4a || this == flac || this == wav;
}

enum DownloadStatus { queued, downloading, completed, failed, cancelled }

class OsaidSearchResult {
  final String id;
  final String title;
  final String author;
  final String duration;
  final String thumbnailUrl;
  final String url;

  OsaidSearchResult({
    required this.id,
    required this.title,
    required this.author,
    required this.duration,
    required this.thumbnailUrl,
    required this.url,
  });
}

class OsaidDownloadTask {
  final String id;
  final String url;
  String title;
  String site;
  OsaidQuality quality;
  DownloadStatus status;
  double progress;
  String statusText;
  String? filePath;

  OsaidDownloadTask({
    required this.id,
    required this.url,
    this.title = 'جاري التحليل...',
    this.site = '',
    required this.quality,
    this.status = DownloadStatus.queued,
    this.progress = 0.0,
    this.statusText = 'في الانتظار',
    this.filePath,
  });
}

class OsaidDownloaderService {
  static const _channel = MethodChannel('com.osaid.file/downloader');
  static const _bridgeChannel = MethodChannel('com.osaid.file/bridge');
  static const _progressChannel = EventChannel('com.osaid.file/downloader_progress');

  final YoutubeExplode _yt = YoutubeExplode();

  Stream<Map<dynamic, dynamic>> get progressStream =>
      _progressChannel.receiveBroadcastStream().map((e) => Map<dynamic, dynamic>.from(e as Map));

  bool isYouTubeUrl(String input) {
    final lower = input.toLowerCase();
    return lower.contains('youtube.com/') || lower.contains('youtu.be/');
  }

  bool isUrl(String input) {
    final trimmed = input.trim().toLowerCase();
    return trimmed.startsWith('http://') || trimmed.startsWith('https://');
  }

  Future<Map<String, dynamic>> getEngineInfo() async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('engineInfo');
      return Map<String, dynamic>.from(res ?? {'ready': false, 'version': 'غير معروف'});
    } catch (e) {
      return {'ready': false, 'version': e.toString()};
    }
  }

  /// بحث فوري عن الأغاني والفيديوهات بالاسم (مثل تطبيق طرب)
  Future<List<OsaidSearchResult>> search(String query) async {
    final results = await _yt.search.search(query);
    return results.take(12).map((v) {
      final d = v.duration;
      final durText = d == null
          ? ''
          : '${d.inMinutes}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';
      return OsaidSearchResult(
        id: v.id.value,
        title: v.title,
        author: v.author,
        duration: durText,
        thumbnailUrl: v.thumbnails.mediumResUrl,
        url: v.url,
      );
    }).toList();
  }

  Future<String> updateEngine() async {
    final res = await _channel.invokeMethod<String>('updateEngine');
    return res ?? 'تم التحديث';
  }

  Future<Map<String, dynamic>> analyzeUrl(String url) async {
    if (isYouTubeUrl(url)) {
      try {
        final v = await _yt.videos.get(url);
        return {
          'title': v.title,
          'site': 'YouTube',
          'duration': v.duration?.toString() ?? '',
        };
      } catch (_) {}
    }
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('analyze', {'url': url});
      return Map<String, dynamic>.from(res ?? {});
    } catch (_) {
      return {'title': url, 'site': 'رابط مباشر', 'duration': ''};
    }
  }

  /// تنزيل مباشر وسريع لليوتيوب في مجلد مؤقت ثم حفظه عبر MediaStore (لا يحتاج أي صلاحيات تخزين)
  Future<String> _downloadYouTubeDirect({
    required String url,
    required OsaidQuality quality,
    required void Function(double progress, String status) onProgress,
  }) async {
    onProgress(0.05, 'جاري جلب روابط البث...');
    final video = await _yt.videos.get(url);
    final manifest = await _yt.videos.streamsClient.getManifest(video.id);

    StreamInfo streamInfo;
    String ext;
    if (quality.isAudioOnly) {
      streamInfo = manifest.audioOnly.withHighestBitrate();
      ext = streamInfo.container.name;
    } else {
      final muxed = manifest.muxed.sortByVideoQuality();
      if (muxed.isEmpty) {
        throw Exception('لم يتوفر بث مدمج');
      }
      streamInfo = muxed.last;
      ext = streamInfo.container.name;
    }

    final tmpDir = await getTemporaryDirectory();
    final safeTitle = video.title
        .replaceAll(RegExp(r'[^\w\u0600-\u06FF\s-]'), '_')
        .replaceAll(RegExp(r'\s+'), '_')
        .trim();
    final shortName = safeTitle.isEmpty
        ? 'video_${DateTime.now().millisecondsSinceEpoch}'
        : (safeTitle.length > 60 ? safeTitle.substring(0, 60) : safeTitle);
    final tempFile = File('${tmpDir.path}/$shortName.$ext');

    final stream = _yt.videos.streamsClient.get(streamInfo);
    final sink = tempFile.openWrite();
    final total = streamInfo.size.totalBytes;
    int received = 0;

    try {
      await for (final chunk in stream) {
        sink.add(chunk);
        received += chunk.length;
        if (total > 0) {
          final pct = received / total;
          onProgress(pct, 'جاري التحميل ${(pct * 100).toStringAsFixed(0)}%');
        }
      }
    } finally {
      await sink.flush();
      await sink.close();
    }

    onProgress(0.98, 'جاري الحفظ في المعرض...');
    if (Platform.isAndroid) {
      final saved = await _bridgeChannel.invokeMethod<String>('saveToPublic', {'path': tempFile.path});
      try {
        await tempFile.delete();
      } catch (_) {}
      return saved ?? tempFile.path;
    }
    return tempFile.path;
  }

  /// تنزيل ذكي مزدوج لكل المنصات:
  /// - يستخدم yt-dlp + FFmpeg (YouTube, TikTok, Instagram, Facebook, X, SoundCloud...)
  /// - وإذا كان الرابط من يوتيوب وتعثر yt-dlp، يتحول فوراً للتحميل المباشر عبر youtube_explode_dart
  Future<String?> startDownload({
    required String id,
    required String url,
    required OsaidQuality quality,
    void Function(double progress, String status)? onDirectProgress,
  }) async {
    try {
      final path = await _channel.invokeMethod<String>('download', {
        'id': id,
        'url': url,
        'quality': quality.key,
      });
      return path;
    } catch (nativeErr) {
      if (isYouTubeUrl(url)) {
        return await _downloadYouTubeDirect(
          url: url,
          quality: quality,
          onProgress: onDirectProgress ?? (_, __) {},
        );
      }
      rethrow;
    }
  }

  Future<void> cancelDownload(String id) async {
    try {
      await _channel.invokeMethod('cancel', {'id': id});
    } catch (_) {}
  }
}
