import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/models/marketplace_job.dart';
import '../../../../core/services/job_service.dart';
import '../../../../shared/widgets/app_background.dart';
import '../../../../shared/widgets/section_card.dart';
import 'hire_freelancer_screen.dart';
import 'post_job_screen.dart';

class JobListScreen extends ConsumerStatefulWidget {
  const JobListScreen({super.key});

  @override
  ConsumerState<JobListScreen> createState() => _JobListScreenState();
}

class _JobListScreenState extends ConsumerState<JobListScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(jobServiceProvider).load();
    });
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).extension<AppColors>()!;
    final jobService = ref.watch(jobServiceProvider);
    final jobs = jobService.jobs;
    final usdFormat = NumberFormat.currency(locale: 'en_US', symbol: r'$');

    return Scaffold(
      backgroundColor: colors.background,
      body: AppBackground(
        child: SafeArea(
          child: RefreshIndicator(
            onRefresh: () => ref.read(jobServiceProvider).load(),
            child: jobs.isEmpty
                ? ListView(
                    padding: const EdgeInsets.all(AppSpacing.xl),
                    children: [
                      const SizedBox(height: AppSpacing.xxl),
                      Icon(Icons.work_outline_rounded, size: 56, color: colors.textMuted),
                      const SizedBox(height: AppSpacing.md),
                      Text('Chưa có việc nào',
                          textAlign: TextAlign.center,
                          style: AppTypography.heading2(colors)),
                      const SizedBox(height: 8),
                      Text(
                        'Đăng một việc để bắt đầu.',
                        textAlign: TextAlign.center,
                        style: AppTypography.bodyMuted(colors),
                      ),
                    ],
                  )
                : ListView.builder(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    itemCount: jobs.length,
                    itemBuilder: (context, index) {
                      final MarketplaceJob job = jobs[index];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: AppSpacing.md),
                        child: SectionCard(
                          child: InkWell(
                            onTap: () => Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => HireFreelancerScreen(job: job)),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(job.title,
                                    style: AppTypography.body(colors)
                                        .copyWith(fontWeight: FontWeight.w700)),
                                const SizedBox(height: 4),
                                Text(job.description,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: AppTypography.bodyMuted(colors)),
                                const SizedBox(height: 8),
                                Text(usdFormat.format(job.budgetUsd),
                                    style: AppTypography.body(colors)
                                        .copyWith(fontWeight: FontWeight.w800, color: colors.primary)),
                              ],
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: colors.primary,
        onPressed: () => Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => const PostJobScreen()),
        ),
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }
}
