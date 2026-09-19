import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';

class AppBackground extends StatelessWidget {
  const AppBackground({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    return Stack(
      fit: StackFit.expand,
      children: [
        Positioned.fill(
          child: RepaintBoundary(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final size = Size(constraints.maxWidth, constraints.maxHeight);
                return CustomPaint(
                  size: size,
                  painter: _SoftGlowBackgroundPainter(colors: colors),
                );
              },
            ),
          ),
        ),
        child,
      ],
    );
  }
}

class _SoftGlowBackgroundPainter extends CustomPainter {
  _SoftGlowBackgroundPainter({required this.colors});
  final AppColors colors;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    canvas.drawRect(rect, Paint()..color = colors.background);

    final glowTopRight = Paint()
      ..shader = ui.Gradient.radial(
        Offset(size.width * 0.9, size.height * 0.02),
        size.width * 0.65,
        [colors.primary.withOpacity(0.14), colors.primary.withOpacity(0.0)],
      );
    canvas.drawRect(rect, glowTopRight);

    final glowBottomLeft = Paint()
      ..shader = ui.Gradient.radial(
        Offset(size.width * 0.05, size.height * 0.55),
        size.width * 0.55,
        [colors.secondary.withOpacity(0.08), colors.secondary.withOpacity(0.0)],
      );
    canvas.drawRect(rect, glowBottomLeft);
  }

  @override
  bool shouldRepaint(covariant _SoftGlowBackgroundPainter oldDelegate) =>
      oldDelegate.colors != colors;
}
