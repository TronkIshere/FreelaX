package com.payment.backend.repository;

import com.payment.backend.entity.PartnerEscrowMock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerEscrowMockRepository extends JpaRepository<PartnerEscrowMock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PartnerEscrowMock> findWithLockByMilestoneId(UUID milestoneId);
    List<PartnerEscrowMock> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(String status, Instant before);
}
