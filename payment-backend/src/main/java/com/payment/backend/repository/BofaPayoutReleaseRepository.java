package com.payment.backend.repository;

import com.payment.backend.entity.BofaPayoutRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BofaPayoutReleaseRepository extends JpaRepository<BofaPayoutRelease, UUID> {
    Optional<BofaPayoutRelease> findByReleaseKey(String releaseKey);
    Optional<BofaPayoutRelease> findByCheckoutOrderId(UUID checkoutOrderId);
}
