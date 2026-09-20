import '../domain/job_repository.dart';
import '../models/marketplace_job.dart';
import '../services/api_client.dart';

/// CHƯA có backend thật đứng sau — endpoint /api/v1/marketplace/jobs/* không
/// tồn tại (marketplace backend đang chờ docs, xem MARKETPLACE_MODULE_REFERENCE.md
/// nếu file đó đã được build). Viết sẵn theo đúng shape request/response đã
/// thiết kế cho module đó, để khi backend xong chỉ cần đổi 1 dòng trong
/// main.dart (JobService.instance.setRepository(RemoteJobRepository()))
/// là chạy được — không cần sửa PostJobScreen/JobListScreen/JobService.
class RemoteJobRepository implements JobRepository {
  RemoteJobRepository({ApiClient? client}) : _client = client ?? apiClient;
  final ApiClient _client;

  @override
  Future<MarketplaceJob> createJob({
    required String title,
    required String description,
    required double budgetUsd,
  }) async {
    try {
      final data = await _client.post('/marketplace/jobs', {
        'title': title,
        'description': description,
        'budgetUsd': budgetUsd,
      });
      if (data is! Map<String, dynamic>) {
        throw const JobRepositoryException('Phản hồi từ server không hợp lệ.');
      }
      return MarketplaceJob.fromJson(data);
    } on ApiException catch (e) {
      throw JobRepositoryException(e.message);
    }
  }

  @override
  Future<List<MarketplaceJob>> listOpenJobs() async {
    try {
      final data = await _client.get('/marketplace/jobs');
      if (data is! List) return const [];
      return data
          .map((e) => MarketplaceJob.fromJson(e as Map<String, dynamic>))
          .toList();
    } on ApiException catch (e) {
      throw JobRepositoryException(e.message);
    }
  }

  @override
  Future<MarketplaceJob> getJobById(String jobId) async {
    try {
      final data = await _client.get('/marketplace/jobs/$jobId');
      if (data is! Map<String, dynamic>) {
        throw const JobRepositoryException('Phản hồi từ server không hợp lệ.');
      }
      return MarketplaceJob.fromJson(data);
    } on ApiException catch (e) {
      throw JobRepositoryException(e.message);
    }
  }
}
