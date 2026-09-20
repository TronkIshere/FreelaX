import '../models/marketplace_job.dart';

class JobRepositoryException implements Exception {
  const JobRepositoryException(this.message);
  final String message;
  @override
  String toString() => message;
}

abstract class JobRepository {
  Future<MarketplaceJob> createJob({
    required String title,
    required String description,
    required double budgetUsd,
  });

  Future<List<MarketplaceJob>> listOpenJobs();

  Future<MarketplaceJob> getJobById(String jobId);
}
