import 'package:flutter/material.dart';

@immutable
class AppColors extends ThemeExtension<AppColors> {
  const AppColors({
    required this.background,
    required this.surface,
    required this.primaryDark,
    required this.primary,
    required this.primaryLight,
    required this.secondary,
    required this.secondaryLight,
    required this.text,
    required this.textMuted,
    required this.success,
    required this.warning,
    required this.danger,
  });

  final Color background;
  final Color surface;
  final Color primaryDark;
  final Color primary;
  final Color primaryLight;
  final Color secondary;
  final Color secondaryLight;
  final Color text;
  final Color textMuted;
  final Color success;
  final Color warning;
  final Color danger;

  static const light = AppColors(
    background: Color(0xFFF7F9FC),
    surface: Color(0xFFFFFFFF),
    primaryDark: Color(0xFF001C64),
    primary: Color(0xFF0070E0),
    primaryLight: Color(0xFFD6E9FF),
    secondary: Color(0xFF00A971),
    secondaryLight: Color(0xFFB6F0D8),
    text: Color(0xFF15181D),
    textMuted: Color(0xFF6B7280),
    success: Color(0xFF1E8E3E),
    warning: Color(0xFFB25E00),
    danger: Color(0xFFD64550),
  );

  @override
  AppColors copyWith({
    Color? background,
    Color? surface,
    Color? primaryDark,
    Color? primary,
    Color? primaryLight,
    Color? secondary,
    Color? secondaryLight,
    Color? text,
    Color? textMuted,
    Color? success,
    Color? warning,
    Color? danger,
  }) {
    return AppColors(
      background: background ?? this.background,
      surface: surface ?? this.surface,
      primaryDark: primaryDark ?? this.primaryDark,
      primary: primary ?? this.primary,
      primaryLight: primaryLight ?? this.primaryLight,
      secondary: secondary ?? this.secondary,
      secondaryLight: secondaryLight ?? this.secondaryLight,
      text: text ?? this.text,
      textMuted: textMuted ?? this.textMuted,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      danger: danger ?? this.danger,
    );
  }

  @override
  AppColors lerp(ThemeExtension<AppColors>? other, double t) {
    if (other is! AppColors) return this;
    return AppColors(
      background: Color.lerp(background, other.background, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      primaryDark: Color.lerp(primaryDark, other.primaryDark, t)!,
      primary: Color.lerp(primary, other.primary, t)!,
      primaryLight: Color.lerp(primaryLight, other.primaryLight, t)!,
      secondary: Color.lerp(secondary, other.secondary, t)!,
      secondaryLight: Color.lerp(secondaryLight, other.secondaryLight, t)!,
      text: Color.lerp(text, other.text, t)!,
      textMuted: Color.lerp(textMuted, other.textMuted, t)!,
      success: Color.lerp(success, other.success, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
    );
  }
}
