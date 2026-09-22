import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AppConfigService extends ChangeNotifier {
  AppConfigService._();
  static final AppConfigService instance = AppConfigService._();

  static const _baseUrlKey = 'paysim_api_base_url';
  static const defaultBaseUrl = '';

  String _baseUrl = defaultBaseUrl;
  bool _loaded = false;

  String get baseUrl => _baseUrl;
  bool get hasBaseUrl => _baseUrl.trim().isNotEmpty;

  Future<void> load() async {
    if (_loaded) return;
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_baseUrlKey);
    _baseUrl = (saved == null || saved.trim().isEmpty) ? defaultBaseUrl : saved;
    _loaded = true;
    notifyListeners();
  }

  Future<void> setBaseUrl(String url) async {
    final trimmed = url.trim();
    final normalized = trimmed.endsWith('/') && trimmed.length > 1
        ? trimmed.substring(0, trimmed.length - 1)
        : trimmed;
    _baseUrl = normalized;
    final prefs = await SharedPreferences.getInstance();
    if (normalized.isEmpty) {
      await prefs.remove(_baseUrlKey);
    } else {
      await prefs.setString(_baseUrlKey, normalized);
    }
    notifyListeners();
  }
}

final appConfigServiceProvider =
    ChangeNotifierProvider<AppConfigService>((ref) {
  return AppConfigService.instance;
});
