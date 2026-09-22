package com.paypal.backend.repository;

import com.paypal.backend.entity.PaypalPayoutRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaypalPayoutReleaseRepository extends JpaRepository<PaypalPayoutRelease, UUID> {

    boolean existsByCheckoutOrderId(UUID checkoutOrderId);

    Optional<PaypalPayoutRelease> findByCheckoutOrderId(UUID checkoutOrderId);
}
