package com.paypal.backend.repository;

import com.paypal.backend.entity.PaypalPayee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaypalPayeeRepository extends JpaRepository<PaypalPayee, UUID> {

    Optional<PaypalPayee> findByUserId(UUID userId);
}
