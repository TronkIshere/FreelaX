package com.marketplace.backend.scheduler;

import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.service.ContractSubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewDeadlineScheduler {
    private final JobSubmissionRepository submissions;
    private final ContractSubmissionService submissionService;

    @Scheduled(initialDelayString = "${review.deadline-initial-delay-ms:30000}",
            fixedDelayString = "${review.deadline-interval-ms:60000}")
    public void processExpiredReviews() {
        Instant now = Instant.now();
        for (UUID submissionId : submissions.findPartnerReminderCandidates(now, PageRequest.of(0, 100))) {
            try { submissionService.remindPartnerReview(submissionId, Instant.now()); }
            catch (Exception ex) { log.warn("Review reminder will retry for submission {}", submissionId); }
        }
        for (UUID submissionId : submissions.findExpiredReviewIds(now, PageRequest.of(0, 100))) {
            try {
                submissionService.autoReview(submissionId, Instant.now());
            } catch (Exception ex) {
                log.warn("Review deadline will retry for submission {}", submissionId);
            }
        }
    }
}
