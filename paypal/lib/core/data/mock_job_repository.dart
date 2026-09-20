import 'package:uuid/uuid.dart';

import '../domain/job_repository.dart';
import '../models/marketplace_job.dart';

/// Implementation trong bộ nhớ — dùng khi chưa có backend marketplace thật
/// (đang chờ docs). Mất dữ liệu khi tắt app. Cùng interface JobRepository
/// với RemoteJobRepository nên đổi sang backend thật sau này chỉ cần đổi
/// 1 dòng trong main.dart, không cần sửa UI.
class MockJobRepository implements JobRepository {
  final List<MarketplaceJob> _jobs = [];

  @override
  Future<MarketplaceJob> createJob({
    required String title,
    required String description,
    required double budgetUsd,
  }) async {
    await Future.delayed(const Duration(milliseconds: 200));
    final job = MarketplaceJob(
      id: const Uuid().v4(),
      title: title,
      description: description,
      budgetUsd: budgetUsd,
      status: 'OPEN',
      createdAt: DateTime.now(),
    );
    _jobs.insert(0, job);
    return job;
  }

  @override
  Future<List<MarketplaceJob>> listOpenJobs() async {
    await Future.delayed(const Duration(milliseconds: 150));
    return List.unmodifiable(_jobs);
  }

  @override
  Future<MarketplaceJob> getJobById(String jobId) async {
    await Future.delayed(const Duration(milliseconds: 100));
    return _jobs.firstWhere(
      (job) => job.id == jobId,
      orElse: () => throw const JobRepositoryException('Không tìm thấy công việc.'),
    );
  }

  @override
  Future<MarketplaceJob> linkCheckoutOrder({
    required String jobId,
    required String checkoutOrderId,
  }) async {
    await Future.delayed(const Duration(milliseconds: 100));
    final index = _jobs.indexWhere((job) => job.id == jobId);
    if (index == -1) {
      throw const JobRepositoryException('Không tìm thấy công việc.');
    }
    final updated = _jobs[index].copyWith(
      checkoutOrderId: checkoutOrderId,
      status: 'IN_PROGRESS',
    );
    _jobs[index] = updated;
    return updated;
  }
}
