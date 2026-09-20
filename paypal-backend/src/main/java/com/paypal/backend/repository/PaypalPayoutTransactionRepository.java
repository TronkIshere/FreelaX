package com.paypal.backend.repository;

import com.paypal.backend.entity.PaypalPayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaypalPayoutTransactionRepository extends JpaRepository<PaypalPayoutTransaction, UUID> {


    boolean existsByPlatformPayoutId(String platformPayoutId);

    List<PaypalPayoutTransaction> findByPayeeId(UUID payeeId);
}
