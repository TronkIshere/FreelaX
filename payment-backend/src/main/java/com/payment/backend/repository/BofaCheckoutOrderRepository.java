package com.payment.backend.repository;

import com.payment.backend.entity.BofaCheckoutOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BofaCheckoutOrderRepository extends JpaRepository<BofaCheckoutOrder, UUID> {
}
