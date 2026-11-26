import 'dart:ui';
import 'package:flutter/material.dart';

class AppTheme {
  static const Color bgDark = Color(0xFF070B14);
  static const Color bgNavy = Color(0xFF0D1526);
  static const Color cardSurface = Color(0xFF131C31);
  static const Color accentCyan = Color(0xFF38BDF8);
  static const Color accentIndigo = Color(0xFF818CF8);
  static const Color accentEmerald = Color(0xFF34D399);
  static const Color accentAmber = Color(0xFFFBBF24);
  static const Color accentRose = Color(0xFFFB7185);
  static const Color textPrimary = Color(0xFFF8FAFC);
  static const Color textSecondary = Color(0xFF94A3B8);

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: bgDark,
      colorScheme: const ColorScheme.dark(
        primary: accentCyan,
        secondary: accentIndigo,
        surface: cardSurface,
        error: accentRose,
      ),
      fontFamily: 'Roboto',
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: textPrimary,
          fontSize: 20,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

/// خلفية احترافية متدرجة مع إضاءة ناعمة (تعمل على الجوالات والآيبادات)
class ProBackground extends StatelessWidget {
  final Widget child;
  const ProBackground({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // التدرج الأساسي الفخم
        Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topRight,
              end: Alignment.bottomLeft,
              colors: [
                Color(0xFF0A1124),
                Color(0xFF070B14),
                Color(0xFF0D162B),
              ],
            ),
          ),
        ),
        // توهج سماوي علوي
        Positioned(
          top: -120,
          right: -80,
          child: Container(
            width: 340,
            height: 340,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                colors: [
                  AppTheme.accentCyan.withOpacity(0.18),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        // توهج بنفسجي سفلي
        Positioned(
          bottom: -140,
          left: -90,
          child: Container(
            width: 380,
            height: 380,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                colors: [
                  AppTheme.accentIndigo.withOpacity(0.16),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        // المحتوى
        SafeArea(child: child),
      ],
    );
  }
}

/// بطاقة زجاجية احترافية (Glassmorphism Card)
class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final Color? borderColor;

  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.borderColor,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(20),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
        child: Container(
          padding: padding,
          decoration: BoxDecoration(
            color: AppTheme.cardSurface.withOpacity(0.68),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: borderColor ?? Colors.white.withOpacity(0.08),
              width: 1.1,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.25),
                blurRadius: 20,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: child,
        ),
      ),
    );
  }
}
