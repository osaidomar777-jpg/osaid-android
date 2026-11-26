import 'dart:async';
import 'package:flutter/services.dart';

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
}

enum DownloadStatus { queued, downloading, completed, failed, cancelled }

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
  static const _progressChannel = EventChannel('com.osaid.file/downloader_progress');

  Stream<Map<dynamic, dynamic>> get progressStream =>
      _progressChannel.receiveBroadcastStream().map((e) => Map<dynamic, dynamic>.from(e as Map));

  Future<Map<String, dynamic>> analyzeUrl(String url) async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('analyze', {'url': url});
      return Map<String, dynamic>.from(res ?? {});
    } catch (e) {
      return {'title': url, 'site': 'رابط مباشر', 'duration': ''};
    }
  }

  Future<String?> startDownload({
    required String id,
    required String url,
    required OsaidQuality quality,
  }) async {
    final path = await _channel.invokeMethod<String>('download', {
      'id': id,
      'url': url,
      'quality': quality.key,
    });
    return path;
  }

  Future<void> cancelDownload(String id) async {
    try {
      await _channel.invokeMethod('cancel', {'id': id});
    } catch (_) {}
  }
}
