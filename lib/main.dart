import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'screens/media_bridge_screen.dart';
import 'screens/osaid_downloader_screen.dart';
import 'screens/text_bridge_screen.dart';
import 'services/usb_bridge_service.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const OsaidFileApp());
}

class OsaidFileApp extends StatelessWidget {
  const OsaidFileApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Osaid File',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      locale: const Locale('ar'),
      supportedLocales: const [Locale('ar'), Locale('en')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: const OsaidHomeShell(),
    );
  }
}

class OsaidHomeShell extends StatefulWidget {
  const OsaidHomeShell({super.key});

  @override
  State<OsaidHomeShell> createState() => _OsaidHomeShellState();
}

class _OsaidHomeShellState extends State<OsaidHomeShell> with WidgetsBindingObserver {
  final _bridge = UsbBridgeService();
  int _selectedIndex = 0;
  UsbBridgeScanResult? _scanResult;
  bool _isScanning = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshBridge();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshBridge();
    }
  }

  Future<void> _refreshBridge() async {
    if (_isScanning) return;
    setState(() => _isScanning = true);
    try {
      final res = await _bridge.scanInbox();
      if (mounted) setState(() => _scanResult = res);
    } finally {
      if (mounted) setState(() => _isScanning = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isWide = MediaQuery.of(context).size.width >= 720;

    final pages = [
      MediaBridgeScreen(
        bridge: _bridge,
        scanResult: _scanResult,
        isScanning: _isScanning,
        onRefresh: _refreshBridge,
      ),
      TextBridgeScreen(
        bridge: _bridge,
        scanResult: _scanResult,
        onRefresh: _refreshBridge,
      ),
      const OsaidDownloaderScreen(),
    ];

    return Scaffold(
      body: ProBackground(
        child: isWide
            ? Row(
                children: [
                  NavigationRail(
                    selectedIndex: _selectedIndex,
                    onDestinationSelected: (i) => setState(() => _selectedIndex = i),
                    backgroundColor: AppTheme.bgNavy.withOpacity(0.55),
                    labelType: NavigationRailLabelType.all,
                    leading: const Padding(
                      padding: EdgeInsets.symmetric(vertical: 20),
                      child: Column(
                        children: [
                          Icon(Icons.bolt_rounded, color: AppTheme.accentCyan, size: 32),
                          SizedBox(height: 4),
                          Text('Osaid', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                        ],
                      ),
                    ),
                    destinations: const [
                      NavigationRailDestination(
                        icon: Icon(Icons.photo_library_outlined),
                        selectedIcon: Icon(Icons.photo_library_rounded),
                        label: Text('الوسائط USB'),
                      ),
                      NavigationRailDestination(
                        icon: Icon(Icons.text_snippet_outlined),
                        selectedIcon: Icon(Icons.text_snippet_rounded),
                        label: Text('النصوص USB'),
                      ),
                      NavigationRailDestination(
                        icon: Icon(Icons.cloud_download_outlined),
                        selectedIcon: Icon(Icons.cloud_download_rounded),
                        label: Text('محمّل أسيد'),
                      ),
                    ],
                  ),
                  Expanded(
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 920),
                        child: pages[_selectedIndex],
                      ),
                    ),
                  ),
                ],
              )
            : pages[_selectedIndex],
      ),
      bottomNavigationBar: isWide
          ? null
          : NavigationBar(
              selectedIndex: _selectedIndex,
              backgroundColor: AppTheme.bgNavy.withOpacity(0.92),
              indicatorColor: AppTheme.accentCyan.withOpacity(0.22),
              onDestinationSelected: (i) => setState(() => _selectedIndex = i),
              destinations: const [
                NavigationDestination(
                  icon: Icon(Icons.photo_library_outlined),
                  selectedIcon: Icon(Icons.photo_library_rounded, color: AppTheme.accentCyan),
                  label: 'الوسائط USB',
                ),
                NavigationDestination(
                  icon: Icon(Icons.text_snippet_outlined),
                  selectedIcon: Icon(Icons.text_snippet_rounded, color: AppTheme.accentIndigo),
                  label: 'النصوص USB',
                ),
                NavigationDestination(
                  icon: Icon(Icons.cloud_download_outlined),
                  selectedIcon: Icon(Icons.cloud_download_rounded, color: AppTheme.accentEmerald),
                  label: 'محمّل أسيد',
                ),
              ],
            ),
    );
  }
}
