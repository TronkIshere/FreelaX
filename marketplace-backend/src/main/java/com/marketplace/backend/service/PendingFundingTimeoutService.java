package com.marketplace.backend.service;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingFundingTimeoutService {
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final EscrowContractRepository escrows;
    private final FundingTransactionRepository funding;
    private final NotificationService notifications;

    @Transactional
    public void process(UUID contractId) {
        Milestone milestone = milestones.findWithLockByContractId(contractId).orElse(null);
        WorkContract contract = contracts.findById(contractId).orElse(null);
        if (milestone == null || contract == null || contract.getCreatedAt() == null
                || contract.getStatus() != ContractStatus.PENDING_FUNDING
                || milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                || escrows.existsByContractId(contractId)) return;
        Job job = jobs.findById(contract.getJobId()).orElse(null);
        if (job == null || job.getStatus() != JobStatus.AWAITING_PAYMENT) return;
        if (contract.getFundingReminderSentAt() == null) {
            contract.setFundingReminderSentAt(java.time.Instant.now());
            notifications.notify(contract.getClientUserId(), NotificationType.FUNDING_REMINDER,
                    "Nhắc funding Milestone", "Bạn còn dưới 24 giờ để funding hợp đồng.", job.getId());
        }
        if (java.time.LocalDateTime.now().isBefore(contract.getCreatedAt().plusHours(48))
                || funding.existsByMilestoneIdAndStatusIn(milestone.getId(),
                    EnumSet.allOf(FundingStatus.class))) return;
        contract.setStatus(ContractStatus.CANCELLED);
        milestone.setStatus(MilestoneStatus.CANCELLED);
        job.setStatus(JobStatus.CANCELLED);
        notifications.notify(contract.getClientUserId(), NotificationType.JOB_CANCELLED,
                "Hết hạn funding", "Hợp đồng đã hủy do không được funding đúng hạn.", job.getId());
        notifications.notify(contract.getFreelancerId(), NotificationType.JOB_CANCELLED,
                "Hết hạn funding", "Hợp đồng đã hủy do không được funding đúng hạn.", job.getId());
    }
}
