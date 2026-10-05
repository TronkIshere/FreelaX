package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.review.ModerateReviewRequest;
import com.marketplace.backend.dto.request.review.SubmitReviewRequest;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class ContractReviewServiceTest {
    @Autowired WorkContractRepository contracts;
    @Autowired MilestoneRepository milestones;
    @Autowired JobRepository jobs;
    @Autowired ContractSettlementRepository settlements;
    @Autowired ContractCancellationRepository cancellations;
    @Autowired ContractReviewRepository reviews;
    @Autowired ReviewAuditRepository audit;
    @Autowired UserRepository users;
    @Autowired TestEntityManager em;
    NotificationService notifications;
    ContractReviewService service;
    WorkContract contract;
    Milestone milestone;
    ContractSettlement settlement;
    UUID client;
    UUID freelancer;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        service = new ContractReviewService(contracts, milestones, jobs, settlements,
                cancellations, reviews, audit, users, notifications);
        client = UUID.randomUUID(); freelancer = UUID.randomUUID();
        Job job = new Job(); job.setTitle("Contract work"); job.setClientUserId(client);
        job.setFreelancerId(freelancer); job.setBudgetUsd(new BigDecimal("100.00"));
        job.setStatus(JobStatus.COMPLETED); job = jobs.saveAndFlush(job);
        contract = new WorkContract(); contract.setJobId(job.getId());
        contract.setClientUserId(client); contract.setFreelancerId(freelancer);
        contract.setTitleSnapshot("Contract work"); contract.setBudgetUsd(new BigDecimal("100.00"));
        contract.setStatus(ContractStatus.COMPLETED); contract = contracts.saveAndFlush(contract);
        milestone = new Milestone(); milestone.setContractId(contract.getId());
        milestone.setAmount(new BigDecimal("100.00")); milestone.setCurrency("USD");
        milestone.setStatus(MilestoneStatus.RELEASED); milestone = milestones.saveAndFlush(milestone);
        settlement = new ContractSettlement(); settlement.setContractId(contract.getId());
        settlement.setMilestoneId(milestone.getId()); settlement.setJobId(job.getId());
        settlement.setFundingTransactionId(UUID.randomUUID()); settlement.setCheckoutOrderId(UUID.randomUUID());
        settlement.setFreelancerId(freelancer); settlement.setAmount(new BigDecimal("100.00"));
        settlement.setCurrency("USD"); settlement.setReleaseKey("release-" + milestone.getId());
        settlement.setMoneyStatus(SettlementMoneyStatus.SUCCEEDED);
        settlement.setMoneySucceededAt(Instant.now().minus(1, ChronoUnit.DAYS));
        settlement = settlements.saveAndFlush(settlement);
    }

    @Test
    void invitesOnlyAfterConfirmedReleaseAndOnlyOnce() {
        settlement.setMoneySucceededAt(null); settlements.saveAndFlush(settlement);
        assertCode(ErrorCode.REVIEW_INELIGIBLE, () -> service.invite(contract.getId()));
        assertThat(reviews.findByContractIdOrderByCreatedAtAsc(contract.getId())).isEmpty();
        settlement.setMoneySucceededAt(Instant.now().minus(1, ChronoUnit.DAYS)); settlements.saveAndFlush(settlement);
        service.invite(contract.getId()); service.invite(contract.getId());
        assertThat(reviews.findByContractIdOrderByCreatedAtAsc(contract.getId())).hasSize(2);
        verify(notifications, times(2)).notify(any(), eq(NotificationType.REVIEW_INVITED), anyString(), anyString(), any());
    }

    @Test
    void oneReviewIsMaskedSecondPublishesBothAndDuplicateIsSafe() {
        service.invite(contract.getId());
        var first = service.submit(client, contract.getId(), request(5, "Great work"));
        assertThat(first.publishedAt()).isNull();
        var freelancerView = service.forContract(freelancer, contract.getId());
        assertThat(freelancerView.stream().filter(x -> x.reviewerId().equals(client)).findFirst().orElseThrow().overall()).isNull();
        assertThat(reviews.findPublishedScores(freelancer)).isEmpty();
        service.submit(client, contract.getId(), request(5, "Great work"));
        assertCode(ErrorCode.REVIEW_CONFLICT, () -> service.submit(client, contract.getId(), request(4, "Changed")));
        service.submit(freelancer, contract.getId(), request(4, "Clear brief"));
        assertThat(reviews.findByContractIdOrderByCreatedAtAsc(contract.getId()))
                .allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
        assertThat(reviews.findPublishedScores(freelancer)).containsExactly(5);
        assertThat(reviews.findPublishedScores(client)).containsExactly(4);
        verify(notifications, times(2)).notify(any(), eq(NotificationType.REVIEW_PUBLISHED), anyString(), anyString(), any());
        assertCode(ErrorCode.REVIEW_NOT_FOUND, () -> service.forContract(UUID.randomUUID(), contract.getId()));
        assertCode(ErrorCode.REVIEW_NOT_FOUND, () -> service.submit(UUID.randomUUID(), contract.getId(), null));
    }

    @Test
    void fourteenDayTimeoutPublishesSingleSubmission() {
        settlement.setMoneySucceededAt(Instant.now().minus(15, ChronoUnit.DAYS)); settlements.saveAndFlush(settlement);
        service.invite(contract.getId());
        var response = service.submit(client, contract.getId(), request(5, "Great work"));
        assertThat(response.publishedAt()).isNotNull();
        assertThat(reviews.findPublishedScores(freelancer)).containsExactly(5);
        assertThat(reviews.findPublishedScores(client)).isEmpty();
    }

    @Test
    void moderationHidesTextButKeepsScoreUntilInvalidation() {
        service.invite(contract.getId());
        var first = service.submit(client, contract.getId(), request(5, "Review text"));
        service.submit(freelancer, contract.getId(), request(4, "Other text"));
        assertCode(ErrorCode.REVIEW_NOT_FOUND, () -> service.report(UUID.randomUUID(), contract.getId(), first.id(), "Unsafe"));
        service.report(freelancer, contract.getId(), first.id(), "Unsafe text");
        assertThat(service.reported(20)).anySatisfy(row -> assertThat(row.id()).isEqualTo(first.id()));
        assertThat(service.adminDetail(first.id()).audit()).singleElement()
                .satisfies(event -> assertThat(event.reason()).isEqualTo("Unsafe text"));
        assertCode(ErrorCode.REVIEW_CONFLICT, () -> service.moderate(client, first.id(),
                new ModerateReviewRequest(ModerateReviewRequest.Action.INVALIDATE, "Self decision")));
        UUID admin = UUID.randomUUID();
        service.moderate(admin, first.id(), new ModerateReviewRequest(ModerateReviewRequest.Action.HIDE_CONTENT, "Policy reason"));
        assertThat(service.reported(20)).noneMatch(row -> row.id().equals(first.id()));
        assertThat(reviews.findPublishedScores(freelancer)).containsExactly(5);
        assertThat(service.forContract(freelancer, contract.getId()).stream()
                .filter(x -> x.id().equals(first.id())).findFirst().orElseThrow().comment()).isNull();
        service.moderate(admin, first.id(), new ModerateReviewRequest(ModerateReviewRequest.Action.INVALIDATE, "Proven abuse"));
        assertThat(reviews.findPublishedScores(freelancer)).isEmpty();
        assertCode(ErrorCode.REVIEW_CONFLICT, () -> service.moderate(admin, first.id(),
                new ModerateReviewRequest(ModerateReviewRequest.Action.HIDE_CONTENT, "Late hide")));
        assertThat(audit.findByReviewIdOrderByCreatedAtAsc(first.id())).hasSize(3);
    }

    @Test
    void cancelledOrRefundedContractNeverInvites() {
        contract.setStatus(ContractStatus.CANCELLED); contracts.saveAndFlush(contract);
        assertCode(ErrorCode.REVIEW_INELIGIBLE, () -> service.invite(contract.getId()));
        assertThat(reviews.findByContractIdOrderByCreatedAtAsc(contract.getId())).isEmpty();
    }

    @Test
    void invalidScoresAndOversizeCommentCannotBeSubmitted() {
        assertCode(ErrorCode.REVIEW_INVALID, () -> service.submit(client, contract.getId(), request(0, "Bad")));
        assertCode(ErrorCode.REVIEW_INVALID, () -> service.submit(client, contract.getId(),
                request(5, "x".repeat(2001))));
        assertThat(reviews.findByContractIdOrderByCreatedAtAsc(contract.getId())).isEmpty();
    }

    @Test
    void databasePreventsSecondReviewForSameRater() {
        service.invite(contract.getId());
        ContractReview duplicate = new ContractReview(); duplicate.setContractId(contract.getId());
        duplicate.setJobId(contract.getJobId()); duplicate.setReviewerId(client);
        duplicate.setRevieweeId(freelancer); duplicate.setCompletedAt(settlement.getMoneySucceededAt());
        assertThatThrownBy(() -> reviews.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schedulerQueriesFindOnlyMissingInvitesAndDueUnpublishedReviews() {
        settlement.setMoneySucceededAt(Instant.now().minus(15, ChronoUnit.DAYS));
        settlements.saveAndFlush(settlement);
        assertThat(settlements.findUninvitedCompletedContractIds(PageRequest.of(0, 50))).contains(contract.getId());
        service.invite(contract.getId());
        assertThat(settlements.findUninvitedCompletedContractIds(PageRequest.of(0, 50))).doesNotContain(contract.getId());
        ContractReview row = reviews.findByContractIdAndReviewerId(contract.getId(), client).orElseThrow();
        row.setSubmittedAt(Instant.now()); row.setOverall(5); row.setCommunication(5);
        row.setRequirementsOrQuality(5); row.setTimeliness(5);
        reviews.saveAndFlush(row);
        assertThat(reviews.findDuePublication(Instant.now().minus(14, ChronoUnit.DAYS), PageRequest.of(0, 50)))
                .contains(contract.getId());
    }

    private SubmitReviewRequest request(int overall, String comment) {
        return new SubmitReviewRequest(overall, new SubmitReviewRequest.Dimensions(5, 4, 5), comment);
    }
    private void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(code);
    }
}
