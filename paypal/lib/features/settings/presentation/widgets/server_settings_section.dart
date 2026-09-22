import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/app_config_service.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../../../../shared/widgets/section_card.dart';

class ServerSettingsSection extends ConsumerStatefulWidget {
  const ServerSettingsSection({super.key});

  @override
  ConsumerState<ServerSettingsSection> createState() =>
      _ServerSettingsSectionState();
}

class _ServerSettingsSectionState extends ConsumerState<ServerSettingsSection> {
  late final TextEditingController _controller;
  bool _saving = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    _controller =
        TextEditingController(text: AppConfigService.instance.baseUrl);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _message = null;
    });
    try {
      await ref.read(appConfigServiceProvider).setBaseUrl(_controller.text);
      if (!mounted) return;
      setState(() => _message = 'Đã lưu địa chỉ server.');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    ref.watch(appConfigServiceProvider);

    return SectionCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Server backend (chế độ debug)',
                style: AppTypography.caption(colors)),
            const SizedBox(height: 4),
            Text(
              'Điện thoại và laptop chạy backend phải cùng mạng WiFi. Nhập IP:port của laptop.',
              style: AppTypography.caption(colors),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _controller,
              keyboardType: TextInputType.url,
              decoration: InputDecoration(
                hintText: 'http://192.168.1.23:8080',
                filled: true,
                fillColor: colors.surface,
                prefixIcon: const Icon(Icons.dns_outlined),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            if (_message != null) ...[
              const SizedBox(height: 8),
              Text(_message!, style: AppTypography.bodyMuted(colors)),
            ],
            const SizedBox(height: AppSpacing.md),
            PrimaryButton(
              label: 'Lưu địa chỉ server',
              isLoading: _saving,
              onPressed: _save,
              icon: Icons.save_outlined,
            ),
          ],
        ),
      ),
    );
  }
}
