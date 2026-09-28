package com.marketplace.backend.repository;

import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.OnRampStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FreelancerPayoutRecordRepository extends JpaRepository<FreelancerPayoutRecord, UUID> {

    Optional<FreelancerPayoutRecord> findByJobId(UUID jobId);

    List<FreelancerPayoutRecord> findByFreelancerId(UUID freelancerId);

    List<FreelancerPayoutRecord> findByOnRampStatusIn(Collection<OnRampStatus> statuses);

    List<FreelancerPayoutRecord> findByOnRampStatus(OnRampStatus status);

    List<FreelancerPayoutRecord> findByOnRampStatusAndOffRampStatus(OnRampStatus onRampStatus, OffRampStatus offRampStatus);

}
