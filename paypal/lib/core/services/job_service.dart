import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/mock_job_repository.dart';
import '../domain/job_repository.dart';
import '../models/marketplace_job.dart';

class JobService extends ChangeNotifier {
  JobService._();
  static final JobService instance = JobService._();

  JobRepository _repository = MockJobRepository();

  List<MarketplaceJob> _jobs = [];
  bool _loaded = false;

  List<MarketplaceJob> get jobs => List.unmodifiable(_jobs);
  bool get isLoaded => _loaded;

  void setRepository(JobRepository repository) {
    _repository = repository;
  }

  Future<void> load() async {
    _jobs = await _repository.listOpenJobs();
    _loaded = true;
    notifyListeners();
  }

  Future<void> createJob({
    required String title,
    required String description,
    required double budgetUsd,
  }) async {
    await _repository.createJob(
      title: title,
      description: description,
      budgetUsd: budgetUsd,
    );
    await load();
  }
}

final jobServiceProvider = ChangeNotifierProvider<JobService>((ref) {
  return JobService.instance;
});
