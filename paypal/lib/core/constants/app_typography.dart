import 'package:flutter/material.dart';

import 'app_colors.dart';

abstract class AppTypography {
  static const TextStyle _heading1 = TextStyle(
    fontSize: 32,
    fontWeight: FontWeight.w800,
    height: 1.2,
  );
  static const TextStyle _heading2 = TextStyle(
    fontSize: 22,
    fontWeight: FontWeight.w700,
    height: 1.25,
  );
  static const TextStyle _body = TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.w400,
    height: 1.5,
  );
  static const TextStyle _caption = TextStyle(
    fontSize: 12,
    fontWeight: FontWeight.w500,
    height: 1.3,
  );
  static const TextStyle _score = TextStyle(
    fontSize: 28,
    fontWeight: FontWeight.w800,
    height: 1.1,
  );

  static TextStyle heading1(AppColors c) => _heading1.copyWith(color: c.text);
  static TextStyle heading2(AppColors c) => _heading2.copyWith(color: c.text);
  static TextStyle body(AppColors c) => _body.copyWith(color: c.text);
  static TextStyle bodyMuted(AppColors c) => _body.copyWith(color: c.textMuted);
  static TextStyle caption(AppColors c) => _caption.copyWith(color: c.textMuted);
  static TextStyle score(AppColors c) => _score.copyWith(color: c.primaryDark);

  static TextTheme buildTextTheme(AppColors c) {
    return TextTheme(
      headlineLarge: heading1(c),
      headlineMedium: heading2(c),
      headlineSmall: heading2(c),
      titleLarge: heading2(c),
      titleMedium: body(c).copyWith(fontWeight: FontWeight.w600),
      bodyLarge: body(c),
      bodyMedium: body(c),
      bodySmall: bodyMuted(c),
      labelLarge: body(c).copyWith(fontWeight: FontWeight.w600),
      labelSmall: caption(c),
    );
  }
}
