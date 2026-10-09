package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.review.ModerateReviewRequest;
import com.marketplace.backend.dto.request.review.SubmitReviewRequest;
import com.marketplace.backend.dto.response.review.ReviewResponse;
import com.marketplace.backend.dto.response.review.AdminReviewDetail;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContractReviewService {
    private static final Duration PUBLICATION_DELAY = Duration.ofDays(14);
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final ContractSettlementRepository settlements;
    private final EscrowContractRepository escrows;
    private final ContractCancellationRepository cancellations;
    private final ContractReviewRepository reviews;
    private final ReviewAuditRepository audit;
    private final UserRepository users;
    private final NotificationService notifications;

    @Transactional
    public void invite(UUID contractId) {
        Context context = eligible(contractId);
        ensureInvitations(context);
    }

    @Transactional
    public ReviewResponse submit(UUID actor, UUID contractId, SubmitReviewRequest request) {
        participantOrNotFound(actor, contractId);
        validate(request);
        Context context = eligible(contractId);
        ensureInvitations(context);
        ContractReview row = reviews.findByContractIdAndReviewerId(contractId, actor).orElseThrow(this::notFound);
        if (row.getSubmittedAt() != null) {
            if (!matches(row, request)) throw new ApplicationException(ErrorCode.REVIEW_CONFLICT);
            return ReviewResponse.from(row, true, true);
        }
        row.setOverall(request.overall());
        row.setCommunication(request.dimensions().communication());
        row.setRequirementsOrQuality(request.dimensions().requirementsOrQuality());
        row.setTimeliness(request.dimensions().timeliness());
        row.setComment(normalize(request.comment()));
        row.setSubmittedAt(Instant.now());
        reviews.saveAndFlush(row);
        publishIfReady(context);
        return ReviewResponse.from(row, true, true);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> forContract(UUID actor, UUID contractId) {
        WorkContract contract = contracts.findById(contractId).orElseThrow(this::notFound);
        if (!participant(actor, contract)) throw notFound();
        return reviews.findByContractIdOrderByCreatedAtAsc(contractId).stream()
                .map(row -> ReviewResponse.from(row,
                        actor.equals(row.getReviewerId()) || row.getPublishedAt() != null && row.getInvalidatedAt() == null,
                        actor.equals(row.getReviewerId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> publicForUser(UUID userId, int page, int size) {
        if (users.findById(userId).filter(User::isEnabled).isEmpty()) throw notFound();
        if (page < 0 || size < 1 || size > 100) throw invalid();
        return reviews.findPublicForUser(userId, PageRequest.of(page, size)).stream()
                .map(row -> ReviewResponse.from(row, true, false)).toList();
    }

    @Transactional
    public ReviewResponse report(UUID actor, UUID contractId, UUID reviewId, String reason) {
        participantOrNotFound(actor, contractId);
        if (!StringUtils.hasText(reason) || reason.length() > 2000) throw invalid();
        milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
        ContractReview row = reviews.findById(reviewId).filter(x -> x.getContractId().equals(contractId))
                .orElseThrow(this::notFound);
        if (!actor.equals(row.getRevieweeId()) || row.getPublishedAt() == null || row.getInvalidatedAt() != null)
            throw notFound();
        if (row.getReportedAt() != null) return ReviewResponse.from(row, true, false);
        row.setReportedBy(actor); row.setReportedAt(Instant.now());
        record(row, actor, "REPORTED", "PUBLISHED", "REPORTED", reason.trim());
        return ReviewResponse.from(row, true, false);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> reported(int size) {
        if (size < 1 || size > 100) throw invalid();
        return reviews.findByReportedAtIsNotNullAndHiddenAtIsNullAndInvalidatedAtIsNullOrderByReportedAtAsc(PageRequest.of(0, size))
                .stream().map(row -> ReviewResponse.from(row, true, true)).toList();
    }

    @Transactional(readOnly = true)
    public AdminReviewDetail adminDetail(UUID reviewId) {
        ContractReview row = reviews.findById(reviewId).orElseThrow(this::notFound);
        return new AdminReviewDetail(ReviewResponse.from(row, true, true),
                audit.findByReviewIdOrderByCreatedAtAsc(reviewId).stream()
                        .map(a -> new AdminReviewDetail.Audit(a.getActorId(), a.getAction(),
                                a.getBeforeState(), a.getAfterState(), a.getReason(), a.getRequestId(), a.getCreatedAt()))
                        .toList());
    }

    @Transactional
    public ReviewResponse moderate(UUID admin, UUID reviewId, ModerateReviewRequest request) {
        if (request == null || request.action() == null || !StringUtils.hasText(request.reason())
                || request.reason().length() > 2000) throw invalid();
        UUID contractId = reviews.findContractId(reviewId).orElseThrow(this::notFound);
        milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
        WorkContract contract = contracts.findById(contractId).orElseThrow(this::notFound);
        ContractReview row = reviews.findById(reviewId).orElseThrow(this::notFound);
        if (participant(admin, contract)) throw new ApplicationException(ErrorCode.REVIEW_CONFLICT);
        if (row.getSubmittedAt() == null || row.getPublishedAt() == null) throw new ApplicationException(ErrorCode.REVIEW_INELIGIBLE);
        if (request.action() == ModerateReviewRequest.Action.HIDE_CONTENT) {
            if (row.getInvalidatedAt() != null) throw new ApplicationException(ErrorCode.REVIEW_CONFLICT);
            if (row.getHiddenAt() == null) {
                row.setHiddenAt(Instant.now());
                record(row, admin, "HIDE_CONTENT", row.getReportedAt() == null ? "PUBLISHED" : "REPORTED",
                        "HIDDEN", request.reason().trim());
            }
        } else if (row.getInvalidatedAt() == null) {
            row.setInvalidatedAt(Instant.now());
            record(row, admin, "INVALIDATE", row.getHiddenAt() != null ? "HIDDEN"
                    : row.getReportedAt() != null ? "REPORTED" : "PUBLISHED", "INVALIDATED", request.reason().trim());
        }
        return ReviewResponse.from(row, true, true);
    }

    @Transactional
    public void publishDue(UUID contractId) {
        Context context = eligible(contractId);
        publishIfReady(context);
    }

    private Context eligible(UUID contractId) {
        Milestone milestone = milestones.findWithLockByContractId(contractId).orElseThrow(this::notFound);
        WorkContract contract = contracts.findById(contractId).orElseThrow(this::notFound);
        Job job = jobs.findById(contract.getJobId()).orElseThrow(this::notFound);
        EscrowContract escrow = escrows.findByContractId(contractId).orElse(null);
        ContractSettlement settlement = escrow == null
                ? settlements.findByContractId(contractId).orElseThrow(this::ineligible) : null;
        Instant completedAt = escrow == null ? settlement.getMoneySucceededAt() : escrow.getReleasedAt();
        if (contract.getStatus() != ContractStatus.COMPLETED || milestone.getStatus() != MilestoneStatus.RELEASED
                || job.getStatus() != JobStatus.COMPLETED || completedAt == null
                || completedAt.isAfter(Instant.now())
                || (escrow == null && settlement.getMoneyStatus() != SettlementMoneyStatus.SUCCEEDED)
                || (escrow != null && (!"RELEASED_RECONCILED".equals(escrow.getLastChainStatus())
                    || !Objects.equals(escrow.getMilestoneId(), milestone.getId())))
                || !Objects.equals(contract.getId(), milestone.getContractId())
                || (settlement != null && !Objects.equals(contract.getId(), settlement.getContractId()))
                || !Objects.equals(contract.getJobId(), job.getId())
                || (settlement != null && !Objects.equals(contract.getJobId(), settlement.getJobId()))
                || (settlement != null && !Objects.equals(milestone.getId(), settlement.getMilestoneId()))
                || !Objects.equals(contract.getClientUserId(), job.getClientUserId())
                || !Objects.equals(contract.getFreelancerId(), job.getFreelancerId())
                || (settlement != null && !Objects.equals(contract.getFreelancerId(), settlement.getFreelancerId()))
                || Objects.equals(contract.getClientUserId(), contract.getFreelancerId())
                || cancellations.findByContractId(contractId).filter(x ->
                    x.getStatus() == CancellationStatus.REFUND_PENDING || x.getStatus() == CancellationStatus.CANCELLED).isPresent())
            throw ineligible();
        return new Context(contract, job, completedAt, escrow == null);
    }

    private void ensureInvitations(Context context) {
        WorkContract contract = context.contract();
        for (UUID actor : List.of(contract.getClientUserId(), contract.getFreelancerId())) {
            UUID reviewee = actor.equals(contract.getClientUserId()) ? contract.getFreelancerId() : contract.getClientUserId();
            ContractReview existing = reviews.findByContractIdAndReviewerId(contract.getId(), actor).orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.getRevieweeId(), reviewee)
                        || !Objects.equals(existing.getJobId(), contract.getJobId())
                        || !Objects.equals(existing.getCompletedAt(), context.completedAt()))
                    throw new ApplicationException(ErrorCode.REVIEW_CONFLICT);
                continue;
            }
            ContractReview row = new ContractReview();
            row.setContractId(contract.getId()); row.setJobId(contract.getJobId());
            row.setReviewerId(actor);
            row.setRevieweeId(reviewee);
            row.setCompletedAt(context.completedAt());
            reviews.saveAndFlush(row);
            notifications.notify(actor, NotificationType.REVIEW_INVITED, "Mời đánh giá hợp đồng",
                    "Hợp đồng đã hoàn thành sau giải ngân; bạn có thể đánh giá đối tác.", contract.getJobId());
        }
    }

    private void publishIfReady(Context context) {
        List<ContractReview> rows = reviews.findByContractIdOrderByCreatedAtAsc(context.contract().getId());
        boolean bothSubmitted = rows.size() == 2 && rows.stream().allMatch(x -> x.getSubmittedAt() != null);
        boolean timeout = !Instant.now().isBefore(context.completedAt().plus(PUBLICATION_DELAY));
        if (!bothSubmitted && !timeout) return;
        for (ContractReview row : rows) {
            if (row.getSubmittedAt() == null || row.getPublishedAt() != null) continue;
            row.setPublishedAt(Instant.now());
            notifications.notify(row.getRevieweeId(), NotificationType.REVIEW_PUBLISHED, "Đánh giá đã công bố",
                    "Một đánh giá về hợp đồng của bạn đã được công bố.", context.contract().getJobId());
        }
    }

    private void validate(SubmitReviewRequest request) {
        if (request == null || request.dimensions() == null || !rating(request.overall())
                || !rating(request.dimensions().communication())
                || !rating(request.dimensions().requirementsOrQuality())
                || !rating(request.dimensions().timeliness())
                || request.comment() != null && request.comment().length() > 2000) throw invalid();
    }
    private boolean rating(Integer value) { return value != null && value >= 1 && value <= 5; }
    private boolean matches(ContractReview row, SubmitReviewRequest request) {
        return Objects.equals(row.getOverall(), request.overall())
                && Objects.equals(row.getCommunication(), request.dimensions().communication())
                && Objects.equals(row.getRequirementsOrQuality(), request.dimensions().requirementsOrQuality())
                && Objects.equals(row.getTimeliness(), request.dimensions().timeliness())
                && Objects.equals(row.getComment(), normalize(request.comment()));
    }
    private String normalize(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private boolean participant(UUID actor, WorkContract contract) {
        return actor.equals(contract.getClientUserId()) || actor.equals(contract.getFreelancerId());
    }
    private void participantOrNotFound(UUID actor, UUID contractId) {
        WorkContract contract = contracts.findById(contractId).orElseThrow(this::notFound);
        if (!participant(actor, contract)) throw notFound();
    }
    private void record(ContractReview row, UUID actor, String action, String before, String after, String reason) {
        ReviewAudit event = new ReviewAudit();
        event.setReviewId(row.getId()); event.setActorId(actor); event.setAction(action);
        event.setBeforeState(before); event.setAfterState(after);
        event.setReason(reason); event.setRequestId(UUID.randomUUID().toString());
        audit.save(event);
    }
    private ApplicationException invalid() { return new ApplicationException(ErrorCode.REVIEW_INVALID); }
    private ApplicationException notFound() { return new ApplicationException(ErrorCode.REVIEW_NOT_FOUND); }
    private ApplicationException ineligible() { return new ApplicationException(ErrorCode.REVIEW_INELIGIBLE); }
    private record Context(WorkContract contract, Job job, Instant completedAt, boolean simulated) {}
}
